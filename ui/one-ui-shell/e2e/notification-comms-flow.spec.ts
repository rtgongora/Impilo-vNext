import { test, expect } from "./fixtures";

test.describe("Notification & communications journey", () => {
  test("omnichannel hub shows comms orchestration rail", async ({ page }) => {
    await page.goto("/omnichannel");
    await expect(page.locator('[data-testid="notification-comms-orchestration-rail"]')).toBeVisible();
  });
});
