import { test, expect } from "./fixtures";

test.describe("Chronic care management", () => {
  test("care plans route is reachable for sample patient", async ({ page }) => {
    await page.goto("/ehr/demo-patient/care-plans");
    await expect(page.locator("body")).toContainText(/care plan|Care Plans/i);
  });
});
