import { test, expect } from "./fixtures";

test.describe("Telemedicine care chain", () => {
  test("hub shows care chain rail and RTC health panel", async ({ page }) => {
    await page.goto("/telemedicine");
    await expect(page.locator('[data-testid="telemedicine-care-chain-rail"]')).toBeVisible();
    await expect(page.locator('[data-testid="telemedicine-rtc-health-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="telemedicine-care-chain-queue-triage"]')).toBeVisible();
    await expect(page.locator('[data-testid="telemedicine-care-chain-telemedicine-new"]')).toBeVisible();
  });
});
