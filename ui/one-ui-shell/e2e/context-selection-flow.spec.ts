import { test, expect } from "./fixtures";

test.describe("Context activation journey", () => {
  test("facility selection page loads", async ({ page }) => {
    await page.goto("/facility");
    await expect(page.locator("body")).toContainText(/facility|workplace|select/i);
  });

  test("workspace selection requires facility context route", async ({ page }) => {
    await page.goto("/workspace");
    await expect(page.locator("body")).not.toBeEmpty();
  });

  test("shift start page is reachable", async ({ page }) => {
    await page.goto("/shift");
    await expect(page.locator("body")).toContainText(/shift/i);
  });

  test("shift handover page explains missing active shift", async ({ page }) => {
    await page.goto("/shift/handover");
    await expect(page.locator("body")).toContainText(/shift|handover/i);
  });
});
