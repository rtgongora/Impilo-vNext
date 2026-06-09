import { test, expect } from "./fixtures";

test.describe("Inpatient admissions and rounds (mocked)", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/internal/v1/inpatient/**", async (route) => {
      const url = new URL(route.request().url());
      const path = url.pathname.replace(/.*\/internal\/v1\/inpatient/, "");
      const method = route.request().method();

      if (method === "GET" && path === "/admissions") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: [
              {
                admissionRef: "f2000000-0000-0000-0000-000000000001",
                patientCpid: "CPID-ZW-00001",
                subjectCpid: "CPID-ZW-00001",
                status: "ADMITTED",
              },
            ],
          }),
        });
      }

      if (method === "GET" && path.includes("/ward-rounds")) {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: [
              {
                roundId: "33333333-3333-3333-3333-333333333301",
                roundType: "MORNING",
                ledBy: "Dr. Tendai Mapfumo",
                status: "IN_PROGRESS",
              },
            ],
          }),
        });
      }

      return route.continue();
    });
  });

  test("admissions list and rounds page show governed data", async ({ page }) => {
    await page.goto("/clinical/inpatient/admissions");
    await expect(page.getByRole("heading", { name: "Inpatient admissions" })).toBeVisible({ timeout: 30_000 });
    await expect(page.locator("body")).toContainText(/CPID-ZW-00001/i);

    await page.goto("/clinical/inpatient/rounds");
    await expect(page.locator("body")).toContainText(/ward round|MORNING|Dr. Tendai/i, { timeout: 30_000 });
  });
});
