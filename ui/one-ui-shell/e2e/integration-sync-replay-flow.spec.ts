import { test, expect } from "./fixtures";

test.describe("Integration sync & replay journey", () => {
  test("integration status shows replay orchestration panel", async ({ page }) => {
    await page.goto("/admin/integration-status");
    await expect(page.locator('[data-testid="integration-sync-replay-orchestration-panel"]')).toBeVisible();
  });
});
