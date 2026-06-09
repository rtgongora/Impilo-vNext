import { test, expect } from "@playwright/test";

const UI_ORIGIN = process.env.PLAYWRIGHT_EXPERIENCE_URL || "http://localhost:3000";
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";

async function stackReachable(): Promise<boolean> {
  try {
    const [ui, bff] = await Promise.all([
      fetch(UI_ORIGIN, { redirect: "manual" }).then((r) => r.ok || r.status === 304 || r.status === 307 || r.status === 302),
      fetch(`${BFF_ORIGIN}/actuator/health`).then((r) => r.ok),
    ]);
    return Boolean(ui && bff);
  } catch {
    return false;
  }
}

test.describe("Telemedicine (compose stack)", () => {
  test.beforeAll(async () => {
    test.skip(!(await stackReachable()), `Start compose — need UI ${UI_ORIGIN} and BFF ${BFF_ORIGIN}`);
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-telemed-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-specialist-001",
          roles: ["CLINICIAN", "SPECIALIST"],
          actorType: "PROVIDER",
        }),
      );
    });
  });

  test("telemedicine hub loads referrals for golden patient", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/telemedicine`);
    await expect(page.getByRole("heading", { name: /Telemedicine|Teleconsult/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.locator("body")).toContainText(/CPID-ZW-00001|chronic care|VIDEO|SCHEDULED/i, { timeout: 30_000 });
  });

  test("RTC health panel reports gateway status", async ({ request }) => {
    const res = await request.get(`${BFF_ORIGIN}/internal/v1/teleconsult/ops/rtc-health`, {
      headers: {
        "X-Tenant-ID": "00000000-0000-4000-8000-000000000001",
        "X-Pod-ID": "national-spine",
        "X-Request-ID": "e2e-rtc-health-req",
        "X-Correlation-ID": "e2e-rtc-health-corr",
      },
    });
    expect(res.ok()).toBeTruthy();
  });
});
