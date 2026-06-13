import { test, expect } from "@playwright/test";

test.describe("Register self-reg and elevation journey", () => {
  test("register page exposes Health ID self-reg and deferred professional elevation", async ({ page }) => {
    await page.goto("/auth/register");
    await expect(page.getByText("Sign up for Impilo")).toBeVisible();
    await expect(page.getByText(/Do you have a Health ID/i)).toBeVisible();
    await expect(page.getByText(/professional access is activated later/i)).toBeVisible();
    await expect(page.getByTestId("impilo-brand-hero")).toBeVisible();
  });

  test("successful citizen registration lands on home with my_life mode", async ({ page }) => {
    await page.route("**/internal/v1/auth/register", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "reg-1",
            type: "registration",
            attributes: {
              status: "REGISTERED",
              token: "reg-token",
              expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
              user: {
                id: "user-reg-1",
                email: "new@impilo.zw",
                displayName: "New Citizen",
                roles: ["CITIZEN"],
                actorType: "CITIZEN",
              },
            },
          },
        }),
      });
    });

    await page.route("**/internal/v1/policy/consent/accept", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ data: {} }) });
    });

    await page.goto("/auth/register");
    await page.getByPlaceholder("Your full name").fill("New Citizen");
    await page.getByPlaceholder("you@example.com or +263...").fill("new@impilo.zw");
    await page.getByPlaceholder("At least 12 characters with upper, lower, digit, and symbol").fill("SecurePass123!");
    await page.getByPlaceholder("Repeat password").fill("SecurePass123!");
    const checkboxes = page.getByRole("checkbox");
    await checkboxes.nth(0).check();
    await checkboxes.nth(1).check();
    await page.getByRole("button", { name: "Create Account" }).click();

    await expect(page).toHaveURL(/\/home/, { timeout: 12_000 });

    const mode = await page.evaluate(() => sessionStorage.getItem("exp:operational_mode"));
    const homeMode = await page.getByTestId("home-operational-mode").textContent();
    expect(mode === "my_life" || homeMode === "my_life").toBeTruthy();
  });
});
