import { test, expect } from "./fixtures";

test.describe("Public health outreach journey", () => {
  test("public health hub shows outreach orchestration panel", async ({ page }) => {
    await page.goto("/public-health");
    await expect(page.locator('[data-testid="public-health-outreach-orchestration-panel"]')).toBeVisible();
  });
});
