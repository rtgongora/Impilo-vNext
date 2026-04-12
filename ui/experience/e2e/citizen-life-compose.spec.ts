import { test, expect } from "@playwright/test";

/**
 * Real-stack citizen flows — **no** `page.route` mocks.
 *
 * Prereqs (host):
 * 1. `docker compose -f compose/experience/docker-compose.yml up` (DB + wellness + BFF + UI on **3020**).
 * 2. Run Playwright with **`PLAYWRIGHT_COMPOSE_E2E=1`** so this project does not spawn a second Next dev server.
 *
 * The compose file sets anonymous security on BFF/wellness for dev (no Keycloak). The browser still calls
 * **NEXT_PUBLIC_BFF_URL** (defaults to `http://localhost:8160` in the client bundle) from the host.
 */
const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3020";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const WELLNESS_ORIGIN = process.env.PLAYWRIGHT_WELLNESS_URL || "http://localhost:8161";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, wellness] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${WELLNESS_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && wellness);
  } catch {
    return false;
  }
}

test.describe("Citizen life (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose first: docker compose -f compose/experience/docker-compose.yml up — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, wellness ${WELLNESS_ORIGIN}. For Playwright use: PLAYWRIGHT_COMPOSE_E2E=1 npm run e2e -- citizen-life-compose.spec.ts --project=chromium`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-e2e-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-e2e-cpid-001",
          email: "citizen@compose.e2e",
          displayName: "Compose E2E Citizen",
          roles: ["CITIZEN", "DEVELOPER"],
          actorType: "CITIZEN",
        }),
      );
    });
  });

  test("wellness hub loads and real activities request completes", async ({ page }) => {
    const activityUrls: string[] = [];
    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/mobile/citizen/wellness/activities")) {
        activityUrls.push(u);
      }
    });

    await page.goto(`${UI_ORIGIN}/wellness`);
    await expect(page.getByRole("heading", { name: "Wellness Hub" })).toBeVisible({ timeout: 60_000 });
    await expect.poll(() => activityUrls.length, { timeout: 60_000 }).toBeGreaterThan(0);
    expect(activityUrls[0]).toContain("patientId=compose-e2e-cpid-001");
    expect(activityUrls[0]).toMatch(/^https?:\/\/localhost:8160\//);
  });

  test("monitoring devices page loads from real BFF", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/monitoring/devices`);
    await expect(page.getByRole("heading", { name: "My Devices" })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText("Connected devices:")).toBeVisible({ timeout: 30_000 });
  });
});
