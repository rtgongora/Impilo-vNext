import { test, expect } from "./fixtures";

test.describe("AI guidance / Nompilo journey", () => {
  test("guidance hub shows orchestration rail with route context", async ({ page }) => {
    await page.goto("/guidance");
    await expect(page.locator('[data-testid="nompilo-guidance-orchestration-rail"]')).toBeVisible();
    await expect(page.locator('[data-testid="nompilo-route-context"]')).toContainText("/guidance");
  });
});
