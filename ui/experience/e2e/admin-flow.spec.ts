import { test, expect } from "./fixtures";

test.describe("Admin workflow", () => {
  test("admin dashboard loads with section cards", async ({ page }) => {
    await page.goto("/admin");

    await expect(page.getByText("Administration")).toBeVisible();
    await expect(page.getByText("Users")).toBeVisible();
    await expect(page.getByText("Roles")).toBeVisible();
    await expect(page.getByText("Policies")).toBeVisible();
    await expect(page.getByText("Audit")).toBeVisible();
    await expect(page.getByText("Consent")).toBeVisible();
  });

  test("user management page renders", async ({ page }) => {
    await page.goto("/admin/users");

    await expect(page.locator("body")).toContainText(/user/i);
  });

  test("audit trail page loads", async ({ page }) => {
    await page.goto("/admin/audit");

    await expect(page.locator("body")).toContainText(/audit/i);
  });

  test("AI Governance page shows all tabs", async ({ page }) => {
    await page.goto("/ai-governance");

    await expect(page.getByText("AI Governance")).toBeVisible();

    // Verify all six tabs are present
    await expect(page.getByRole("button", { name: /overview/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /datasets/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /rules/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /decisions/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /policy/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /audit/i })).toBeVisible();
  });

  test("AI Governance tab switching works", async ({ page }) => {
    await page.goto("/ai-governance");

    await page.getByRole("button", { name: /datasets/i }).click();
    // After clicking, the Datasets tab should be active (indicated by styling)
    await expect(page.getByRole("button", { name: /datasets/i })).toHaveClass(/cyan/);
  });

  test("admin policies page is accessible", async ({ page }) => {
    await page.goto("/admin/policies");

    await expect(page.locator("body")).toContainText(/polic/i);
  });

  test("admin roles page is accessible", async ({ page }) => {
    await page.goto("/admin/roles");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("system settings accessible from admin section", async ({ page }) => {
    await page.goto("/settings");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });
});
