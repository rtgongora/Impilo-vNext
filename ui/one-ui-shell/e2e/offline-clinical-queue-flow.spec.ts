import { test, expect } from "./fixtures";

test.describe("Offline clinical queue journey", () => {
  test("clinical tools offline tab shows queue orchestration panel", async ({ page }) => {
    await page.goto("/clinical-tools");
    await page.getByRole("button", { name: /Offline Sync/i }).click();
    await expect(page.locator('[data-testid="offline-clinical-queue-orchestration-panel"]')).toBeVisible();
  });
});
