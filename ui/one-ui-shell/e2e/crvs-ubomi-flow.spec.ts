import { test, expect } from "./fixtures";

test.describe("CRVS / UBOMI journey", () => {
  test("UBOMI shell shows CRVS orchestration rail", async ({ page }) => {
    await page.goto("/ubomi");
    await expect(page.locator('[data-testid="crvs-ubomi-orchestration-rail"]')).toBeVisible();
  });
});
