import { test, expect } from "./fixtures";

test.describe("Marketplace order journey", () => {
  test("marketplace hub shows order orchestration rail", async ({ page }) => {
    await page.goto("/marketplace");
    await expect(page.locator('[data-testid="marketplace-order-orchestration-rail"]')).toBeVisible();
  });
});
