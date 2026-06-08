import { test, expect } from "./fixtures";

test.describe("Social / community journey", () => {
  test("social timeline shows community orchestration rail", async ({ page }) => {
    await page.goto("/social");
    await expect(page.locator('[data-testid="social-community-orchestration-rail"]')).toBeVisible();
  });
});
