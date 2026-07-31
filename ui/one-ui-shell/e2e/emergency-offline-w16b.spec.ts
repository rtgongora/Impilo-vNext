/**
 * W16b — service worker + Tier-B offline triage (requires a production Next build).
 *
 * Run:
 *   PLAYWRIGHT_PROD_BUILD=1 npx playwright test e2e/emergency-offline-w16b.spec.ts
 *
 * `next dev` does not activate service workers reliably; the plan requires a prod build.
 */
import { test as base, expect } from "@playwright/test";
import path from "node:path";
import fs from "node:fs";

const REPORT_DIR = path.resolve(
  __dirname,
  "../../../reports/journeys/emergency-pack-w16b",
);

const driveUser = {
  id: "local-offline-provider-001",
  email: "offline@impilo.local",
  displayName: "Dr Offline Drive",
  roles: ["CLINICIAN", "ADMIN", "QUEUE", "FACILITY_ADMIN"],
  actorType: "PROVIDER" as const,
  providerId: "PRV-OFFLINE-001",
  staffId: "STF-OFFLINE-001",
  assuranceLevel: "VERIFIED" as const,
  providerActivated: true,
  loginMethod: "provider_id" as const,
};

const driveFacility = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY"],
};

const test = base.extend({
  page: async ({ page, context }, use) => {
    await context.addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(
      (data) => {
        sessionStorage.setItem("exp:auth_token", "local-offline-token");
        sessionStorage.setItem("exp:auth_user", JSON.stringify(data.user));
        sessionStorage.setItem("exp:provider_id", data.user.providerId);
        sessionStorage.setItem("exp:assurance_level", data.user.assuranceLevel);
        sessionStorage.setItem("exp:facility", JSON.stringify(data.facility));
        sessionStorage.setItem(
          "exp:workspace",
          JSON.stringify({ id: "ws-ed-001", name: "Emergency Department", type: "CLINICAL" }),
        );
        sessionStorage.setItem(
          "exp:shift",
          JSON.stringify({ id: "shift-001", startedAt: new Date().toISOString(), workspace: "ED" }),
        );
        localStorage.setItem(
          "exp:consent_accepted",
          JSON.stringify({
            acceptedAt: new Date().toISOString(),
            version: "2026-04-11",
            userId: data.user.id,
          }),
        );
        localStorage.setItem("exp:consent_version", "2026-04-11");
      },
      { user: driveUser, facility: driveFacility },
    );
    await use(page);
  },
});

test.describe("W16b emergency offline (prod build)", () => {
  test.beforeAll(() => {
    test.skip(
      process.env.PLAYWRIGHT_PROD_BUILD !== "1",
      "Set PLAYWRIGHT_PROD_BUILD=1 (next start) — SW is not reliable under next dev",
    );
    fs.mkdirSync(REPORT_DIR, { recursive: true });
  });

  test("registers impilo-sw at scope / and serves the feature manifest", async ({ page }) => {
    await page.goto("/clinical/emergency");
    await page.waitForFunction(
      async () => {
        const regs = await navigator.serviceWorker.getRegistrations();
        return regs.some((r) =>
          (r.active?.scriptURL || r.installing?.scriptURL || r.waiting?.scriptURL || "").includes(
            "impilo-sw",
          ),
        );
      },
      null,
      { timeout: 30_000 },
    );

    const manifest = await page.evaluate(async () => {
      const res = await fetch("/offline-feature-manifest.json");
      return res.json();
    });
    expect(manifest.features.emergency.enrolled).toBe(true);
    expect(manifest.cacheName).toMatch(/impilo-emergency/);

    await page.screenshot({ path: path.join(REPORT_DIR, "sw-registered.png"), fullPage: true });
  });

  test("Tier-B: offline triage shows NOT_TRIAGEABLE_OFFLINE and never a fabricated acuity", async ({
    page,
  }) => {
    await page.route("**/internal/v1/ed/visits/**", async (route) => {
      if (route.request().method() !== "GET") {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "visit-offline-w16b",
            status: "REGISTERED",
            patient_cpid: "CPID-OFFLINE",
            zone: "WAITING",
            arrival_mode: "WALK_IN",
            journey_id: "j-offline",
            chief_complaint: "Chest pain",
            triage_assessments: [],
            updated_at: new Date().toISOString(),
          },
        }),
      });
    });

    // Stub noisy identity reads so the guard does not bounce the page.
    await page.route("**/internal/v1/**", async (route) => {
      const url = route.request().url();
      if (url.includes("/ed/visits/")) {
        await route.fallback();
        return;
      }
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: [] }),
        });
        return;
      }
      await route.continue();
    });

    await page.goto("/clinical/emergency/visit-offline-w16b");
    await expect(page.getByText("Structured triage")).toBeVisible({ timeout: 30_000 });

    await page.context().setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));

    const banner = page.getByTestId("not-triageable-offline");
    await expect(banner).toBeVisible({ timeout: 15_000 });
    await expect(banner).toHaveText("NOT_TRIAGEABLE_OFFLINE");

    const alert = page.getByTestId("triage-error");
    const text = await alert.innerText();
    expect(text).toContain("NOT_TRIAGEABLE_OFFLINE");
    expect(text).not.toMatch(/\bAcuity\s*[1-5]\b/i);

    await expect(page.getByTestId("complete-triage")).toBeDisabled();
    await expect(page.getByTestId("ed-visit-staleness")).toContainText(/Offline copy|Unavailable/i);

    await page.screenshot({
      path: path.join(REPORT_DIR, "not-triageable-offline.png"),
      fullPage: true,
    });
  });
});
