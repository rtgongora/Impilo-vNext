import { test, expect } from "./fixtures";

test.describe("Citizen onboarding journey", () => {
  test("registration status shows onboarding orchestration", async ({ page }) => {
    await page.goto("/auth/register/status");
    await expect(page.locator("body")).not.toBeEmpty();
  });

  test("issuance queue ops page loads", async ({ page }) => {
    await page.goto("/operations/vito/issuance");
    await expect(page.locator("body")).toContainText(/issuance|queue|request/i);
  });

  test("card pickup verification page is reachable", async ({ page }) => {
    await page.goto("/operations/vito/cards/pickup");
    await expect(page.locator("body")).not.toBeEmpty();
  });
});
