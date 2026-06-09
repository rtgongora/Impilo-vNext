import { test, expect } from "@playwright/test";

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";
const PCT_ORIGIN = process.env.PLAYWRIGHT_PCT_URL || "http://localhost:8088";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff, pct] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
      fetch(`${PCT_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff && pct);
  } catch {
    return false;
  }
}

test.describe("PCT queue (compose stack, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(
      !(await stackReachable()),
      `Start compose: ./tools/dev/up.sh — need UI ${UI_ORIGIN}, BFF ${BFF_ORIGIN}, pct ${PCT_ORIGIN}`,
    );
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-pct-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-provider-001",
          email: "provider@compose.pct",
          displayName: "Compose PCT Clinician",
          roles: ["CLINICIAN"],
          actorType: "PROVIDER",
          facilityId: "f1000000-0000-0000-0000-000000000001",
        }),
      );
    });
  });

  test("queue hub shows governed seed patient", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/queue`);
    await expect(page.getByRole("heading", { name: /Patient Queue|Queue/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.locator("body")).toContainText(/CPID-ZW-00001|Harare Central OPD/i, { timeout: 30_000 });
  });
});
