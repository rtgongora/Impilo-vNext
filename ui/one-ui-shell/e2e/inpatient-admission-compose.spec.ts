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

test.describe("Inpatient admission rounds (compose, no mocks)", () => {
  test.beforeAll(async () => {
    test.skip(!(await stackReachable()), `Start compose — need UI ${UI_ORIGIN} and BFF ${BFF_ORIGIN}`);
  });

  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "compose-inpatient-dev-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "compose-clinician-001",
          roles: ["CLINICIAN"],
          actorType: "PROVIDER",
          facilityId: "f1000000-0000-0000-0000-000000000001",
        }),
      );
    });
  });

  test("admissions and rounds against real inpatient-service", async ({ page }) => {
    await page.goto(`${UI_ORIGIN}/clinical/inpatient/admissions`);
    await expect(page.getByRole("heading", { name: /Admissions/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.locator("body")).toContainText(/CPID-ZW-00001/i, { timeout: 30_000 });

    await page.goto(`${UI_ORIGIN}/clinical/inpatient/rounds`);
    await expect(page.locator("body")).toContainText(/Dr. Tendai Mapfumo|MORNING|ward round/i, { timeout: 30_000 });
  });
});
