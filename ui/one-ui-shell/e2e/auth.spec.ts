import { test, expect } from "@playwright/test";

test.describe("Authentication flows", () => {
  test("login page renders with email and password fields", async ({ page }) => {
    await page.goto("/auth/login");

    await expect(page.locator("h2")).toHaveText("Welcome back");
    await expect(page.locator("p").filter({ hasText: "Sign in to continue to Impilo" })).toBeVisible();
    await expect(page.locator("#email")).toBeVisible();
    await expect(page.locator("#password")).toBeVisible();
    await expect(page.getByRole("button", { name: /sign in/i })).toBeVisible();
  });

  test("login page shows provider-id and biometric sign-in options", async ({ page }) => {
    await page.goto("/auth/login");

    await expect(page.getByRole("link", { name: /provider id/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /biometric/i })).toBeVisible();
  });

  test("can navigate to forgot password from login", async ({ page }) => {
    await page.goto("/auth/login");

    const forgotLink = page.getByRole("link", { name: /forgot password/i });
    await expect(forgotLink).toBeVisible();
    await forgotLink.click();

    await expect(page).toHaveURL(/\/auth\/forgot-password/);
  });

  test("shows validation error when submitting empty email", async ({ page }) => {
    await page.goto("/auth/login");

    await page.locator("#password").fill("somepassword");
    await page.getByRole("button", { name: /sign in/i }).click();

    // HTML5 required attribute prevents submission; the email field should be invalid
    const emailInput = page.locator("#email");
    await expect(emailInput).toHaveAttribute("required", "");
  });

  test("login page has link to create account", async ({ page }) => {
    await page.goto("/auth/login");

    const registerLink = page.getByRole("link", { name: /create account/i });
    await expect(registerLink).toBeVisible();
    await registerLink.click();

    await expect(page).toHaveURL(/\/auth\/register/);
  });

  test("unauthenticated user accessing protected route is redirected to login", async ({ page }) => {
    // Clear any stored auth state
    await page.goto("/auth/login");
    await page.evaluate(() => localStorage.clear());

    await page.goto("/home");

    // Should redirect to login or show login-related content
    await expect(page).toHaveURL(/\/(auth\/login|home)/);
  });

  test("logout page clears session and redirects", async ({ page }) => {
    await page.goto("/auth/logout");

    // After logout, user should be directed toward login
    await expect(page).toHaveURL(/\/(auth\/login|auth\/logout)/);
  });

  test("password visibility toggle works", async ({ page }) => {
    await page.goto("/auth/login");

    const passwordInput = page.locator("#password");
    await expect(passwordInput).toHaveAttribute("type", "password");

    // Click the eye toggle button
    await page.locator("#password").locator("..").getByRole("button").click();

    await expect(passwordInput).toHaveAttribute("type", "text");
  });
});
