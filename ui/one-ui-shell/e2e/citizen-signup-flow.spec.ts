import { test, expect } from "@playwright/test";

test.describe("Citizen signup golden path", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/internal/v1/auth/register/readiness", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            type: "registration-readiness",
            attributes: { ready: true, code: "READY", message: "Registration service is available." },
          },
          meta: { request_id: "e2e", correlation_id: "e2e" },
        }),
      });
    });
  });

  test("successful register routes through assurance after auto-login", async ({ page }) => {
    await page.route("**/internal/v1/auth/register", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "user-e2e-001",
            type: "session",
            attributes: {
              token: "e2e-register-token",
              expiresAt: new Date(Date.now() + 3600_000).toISOString(),
              user: {
                id: "user-e2e-001",
                email: "citizen.e2e@impilo.zw",
                displayName: "Citizen E2E",
                roles: ["CITIZEN"],
                actorType: "CITIZEN",
              },
            },
          },
          meta: { request_id: "e2e", correlation_id: "e2e" },
        }),
      });
    });

    await page.route("**/internal/v1/identity/assurance/upgrade/request", async (route) => {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            upgradeRequestId: "UPG-E2E001",
            targetLevel: "LOA1",
            status: "PENDING",
          },
          meta: { request_id: "e2e", correlation_id: "e2e" },
        }),
      });
    });

    await page.route("**/internal/v1/policy/consent**", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    await page.goto("/auth/register");
    await page.getByLabel(/full name/i).fill("Citizen E2E");
    await page.getByLabel(/email or phone/i).fill("citizen.e2e@impilo.zw");
    await page.getByLabel(/^password$/i).fill("TestPass123!");
    await page.getByLabel(/confirm password/i).fill("TestPass123!");
    await page.getByLabel(/privacy policy/i).check();
    await page.getByLabel(/terms of use/i).check();
    await page.getByRole("button", { name: /sign up|create account/i }).click();

    await expect(page).toHaveURL(/\/auth\/register\/assurance/);
    await page.getByText(/basic access/i).click();
    await page.getByRole("button", { name: /continue/i }).click();
    await expect(page).toHaveURL(/\/consent/);
  });

  test("registration readiness unavailable blocks sign-up UX", async ({ page }) => {
    await page.route("**/internal/v1/auth/register/readiness", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            type: "registration-readiness",
            attributes: { ready: false, code: "AUTH_SERVICE_UNAVAILABLE", message: "Unavailable" },
          },
          meta: { request_id: "e2e", correlation_id: "e2e" },
        }),
      });
    });

    await page.goto("/auth/register");
    await expect(page.getByText(/temporarily unavailable/i)).toBeVisible();
  });
});
