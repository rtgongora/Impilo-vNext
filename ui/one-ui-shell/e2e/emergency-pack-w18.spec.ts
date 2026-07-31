/**
 * W18 — full-chain emergency pack browser drive (J-EP-B2).
 *
 * Single tour against a LIVE local estate (same chain as W15):
 *   next → identity harness → experience-bff → pct → postgres
 *
 * Screenshots: reports/journeys/emergency-pack-w18/
 *
 *   bash scripts/dev/emergency-drive-rig.sh up
 *   PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://localhost:3007 \
 *     npx playwright test e2e/emergency-pack-w18.spec.ts --project=chromium --workers=1
 */

import { test as base, expect } from "@playwright/test";
import type { Page } from "@playwright/test";

const FACILITY_ID = process.env.DRIVE_FACILITY_ID ?? "11111111-1111-1111-1111-111111111111";
const PROVIDER_ID = process.env.DRIVE_PROVIDER_ID ?? "PRV-DRIVE-001";
const HEALTH_ID = process.env.DRIVE_HEALTH_ID ?? "local-drive-provider-001";

const driveUser = {
  id: HEALTH_ID,
  email: "drive@impilo.local",
  displayName: "Dr Local Drive",
  roles: ["CLINICIAN", "ADMIN", "QUEUE", "FACILITY_ADMIN"],
  actorType: "PROVIDER" as const,
  providerId: PROVIDER_ID,
  staffId: "STF-DRIVE-001",
  assuranceLevel: "VERIFIED" as const,
  providerActivated: true,
  loginMethod: "provider_id" as const,
};

const driveFacility = {
  id: FACILITY_ID,
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB", "IMAGING"],
};

const test = base.extend({
  page: async ({ page, context }, use) => {
    await context.addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(
      (data) => {
        sessionStorage.setItem("exp:auth_token", "local-drive-token");
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
          JSON.stringify({ acceptedAt: new Date().toISOString(), version: "2026-04-11", userId: data.user.id }),
        );
        localStorage.setItem("exp:consent_version", "2026-04-11");
      },
      { user: driveUser, facility: driveFacility },
    );
    await use(page);
  },
});

test.beforeAll(async ({ request, baseURL }) => {
  let reachable = false;
  try {
    const res = await request.get(
      `${baseURL}/internal/v1/emergency-episodes/command-summary?facilityId=${FACILITY_ID}`,
      {
        headers: {
          "X-Tenant-ID": "00000000-0000-4000-8000-000000000001",
          "X-Pod-ID": "national-spine",
          "X-Request-ID": "emergency-w18-drive-probe",
          "X-Correlation-ID": "emergency-w18-drive-probe",
          "X-Facility-ID": FACILITY_ID,
          "X-Actor-ID": HEALTH_ID,
          "X-Actor-Type": "PROVIDER",
        },
        timeout: 10_000,
      },
    );
    reachable = res.ok();
  } catch {
    reachable = false;
  }
  test.skip(!reachable, "live estate not reachable — see docs/clinical/emergency-domain-pack/browser-drive.md");
});

async function shot(page: Page, name: string) {
  await page.screenshot({
    path: `../../reports/journeys/emergency-pack-w18/${name}.png`,
    fullPage: true,
  });
}

test.describe("emergency pack W18 — full-chain browser drive (J-EP-B2)", () => {
  test.setTimeout(120_000);

  test("command → activation → observation → disposition → pre-arrival → analytics → MH", async ({
    page,
  }) => {
    await page.goto("/clinical/emergency/command");
    await expect(page).toHaveURL(/\/clinical\/emergency\/command$/);
    await expect(page.getByTestId("command-board")).toBeVisible({ timeout: 45_000 });
    await expect(page.getByTestId("command-board-error")).toHaveCount(0);
    await shot(page, "01-command-board");

    await page.goto("/clinical/emergency/activation");
    await expect(page.getByTestId("activation-form")).toBeVisible({ timeout: 30_000 });
    await page.getByTestId("activation-entry-route").selectOption("AMBULANCE");
    await page.getByTestId("activation-episode-class").selectOption("TRAUMA");

    const created = page.waitForResponse(
      (r) => r.url().endsWith("/internal/v1/emergency-episodes") && r.request().method() === "POST",
    );
    await page.getByTestId("activation-submit").click();
    const response = await created;
    expect(response.status(), await response.text()).toBe(201);
    await expect(page).toHaveURL(/\/clinical\/emergency\/spine\/[0-9a-f-]{36}$/, { timeout: 20_000 });
    await expect(page.getByTestId("activation-error")).toHaveCount(0);
    await shot(page, "02-activation-spine");

    const episodeId = new URL(page.url()).pathname.split("/").pop() as string;

    await page.goto(`/clinical/emergency/spine/${episodeId}/observation`);
    await expect(page.getByTestId("observation-unreadable")).toHaveCount(0);
    await expect(page.getByTestId("observation-panel")).toBeVisible();
    await shot(page, "03-observation");

    await page.goto(`/clinical/emergency/spine/${episodeId}/disposition`);
    await expect(page.getByTestId("disposition-unreadable")).toHaveCount(0);
    await expect(page.getByTestId("disposition-form")).toBeVisible();
    await page.getByTestId("disposition-type").selectOption("TRANSFERRED_OUT");
    await expect(page.getByTestId("disposition-destination")).toBeVisible();
    await shot(page, "04-disposition");

    await page.goto("/clinical/emergency/pre-arrival");
    await expect(page.getByTestId("pre-arrival-form")).toBeVisible();
    await expect(page.getByTestId("pre-arrival-unreadable")).toHaveCount(0);
    await shot(page, "05-pre-arrival");

    await page.goto("/clinical/emergency/analytics");
    await expect(page.getByTestId("analytics-w17-live")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("analytics-live-measures")).toBeVisible();
    await expect(page.getByTestId("analytics-pending-measures")).toBeVisible();
    await expect(page.getByTestId("analytics-pending-measure").first()).toBeVisible();
    await expect(page.getByTestId("analytics-pending-measures")).not.toContainText(/\b0\b/);
    await shot(page, "06-analytics");

    await page.goto("/work/mental-health");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 30_000 });
    await shot(page, "07-mental-health");

    await page.goto("/work/mental-health/restraint-review");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await shot(page, "08-restraint-review");
  });
});
