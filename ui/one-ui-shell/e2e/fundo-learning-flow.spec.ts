import { test, expect } from "./fixtures";

test.describe("Fundo / learning journey", () => {
  test("learning hub shows Fundo orchestration rail", async ({ page }) => {
    await page.goto("/learning");
    await expect(page.locator('[data-testid="fundo-learning-orchestration-rail"]')).toBeVisible();
  });
});
