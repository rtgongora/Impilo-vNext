import { test, expect } from "./fixtures";

test.describe("Provider login and role activation", () => {
  test("provider activate page loads activation rail", async ({ page }) => {
    await page.goto("/provider/activate");
    await expect(page.locator("body")).toContainText(/Activate Provider Role|provider/i);
  });

  test("auth resolving route is reachable", async ({ page }) => {
    await page.goto("/auth/resolving");
    await expect(page.locator("body")).toContainText(/Preparing your experience|identity/i);
  });
});
