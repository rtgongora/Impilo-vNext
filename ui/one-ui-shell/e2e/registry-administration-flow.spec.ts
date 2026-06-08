import { test, expect } from "./fixtures";

test.describe("Registry administration journey", () => {
  test("registry admin landing shows orchestration rail", async ({ page }) => {
    await page.goto("/registry-admin");
    await expect(page.locator('[data-testid="registry-administration-orchestration-rail"]')).toBeVisible();
    await expect(page.locator("body")).toContainText(/Registry administration/i);
  });
});
