import { test, expect } from "./fixtures";

test.describe("Device system event journey", () => {
  test("admin device management page loads registry", async ({ page }) => {
    await page.goto("/admin/devices");
    await expect(page.locator("body")).toContainText(/Device Management|device/i);
  });
});
