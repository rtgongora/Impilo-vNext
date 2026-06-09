import { test, expect } from "./fixtures";

test.describe("Imaging order viewer (mocked)", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/internal/v1/imaging/**", async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.includes("/studies") && route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: [
              {
                id: 1,
                studyUid: "1.2.840.IMPILO.DEMO.CHEST.XRAY.00001",
                patientCpid: "CPID-ZW-00001",
                modality: "DX",
                description: "Chest X-ray — demo study",
                status: "AVAILABLE",
              },
            ],
          }),
        });
      }
      return route.continue();
    });
  });

  test("EHR imaging workspace lists governed studies", async ({ page }) => {
    await page.goto("/ehr/CPID-ZW-00001/imaging");
    await expect(page.getByText(/Imaging \/ PACS/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.locator("body")).toContainText(/1\.2\.840\.IMPILO|Governed imaging/i, { timeout: 15_000 });
  });
});
