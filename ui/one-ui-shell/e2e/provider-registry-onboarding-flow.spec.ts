import { test, expect } from "./fixtures";

test.describe("Provider registry onboarding journey", () => {
  test("provider registry shows onboarding orchestration rail", async ({ page }) => {
    await page.goto("/registry/providers");
    await expect(page.locator('[data-testid="provider-registry-onboarding-orchestration-rail"]')).toBeVisible();
  });
});
