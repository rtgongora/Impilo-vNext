import { test, expect } from "./fixtures";

test.describe("Telemedicine encounter lifecycle", () => {
  test("telemedicine hub shows orchestration rail", async ({ page }) => {
    await page.goto("/telemedicine");
    await expect(page.locator('[data-testid="telemedicine-encounter-orchestration-rail"]')).toBeVisible();
    await expect(page.locator("body")).toContainText(/Telemedicine|teleconsult/i);
  });

  test("new teleconsult composer is reachable", async ({ page }) => {
    await page.goto("/telemedicine/new");
    await expect(page.locator("body")).not.toBeEmpty();
  });
});
