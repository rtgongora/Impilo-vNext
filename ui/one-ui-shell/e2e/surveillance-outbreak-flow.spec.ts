import { test, expect } from "./fixtures";

test.describe("Surveillance / outbreak journey", () => {
  test("surveillance dashboard shows outbreak orchestration panel", async ({ page }) => {
    await page.goto("/public-health/surveillance");
    await expect(page.locator('[data-testid="surveillance-outbreak-orchestration-panel"]')).toBeVisible();
  });
});
