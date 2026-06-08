import { test, expect } from "./fixtures";

test.describe("Dispatch / delivery journey", () => {
  test("dispatch operations shows orchestration panel", async ({ page }) => {
    await page.goto("/operations/dispatch");
    await expect(page.locator('[data-testid="dispatch-delivery-orchestration-panel"]')).toBeVisible();
  });
});
