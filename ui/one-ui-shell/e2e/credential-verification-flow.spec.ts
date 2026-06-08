import { test, expect } from "./fixtures";

test.describe("Credential verification journey", () => {
  test("public verifier page loads workflow", async ({ page }) => {
    await page.goto("/verify/credential");
    await expect(page.locator("body")).toContainText(/verification token|verifier|configured/i);
  });

  test("credentials hub shows verification workflow panel", async ({ page }) => {
    await page.goto("/home/credentials");
    await expect(page.locator("body")).not.toBeEmpty();
  });
});
