import { test, expect } from "./fixtures";

test.describe("Reporting dashboard journey", () => {
  test("reports hub shows orchestration panel", async ({ page }) => {
    await page.goto("/reports");
    await expect(page.locator('[data-testid="reporting-dashboard-orchestration-panel"]')).toBeVisible();
    await expect(page.locator("body")).toContainText(/Reports/i);
  });
});
