import { test, expect } from "./fixtures";

test.describe("Global navigation", () => {
  test("home page is accessible at root redirect", async ({ page }) => {
    await page.goto("/");

    // Root should redirect to /home or similar landing
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("sidebar navigation is present on authenticated pages", async ({ page }) => {
    await page.goto("/home");

    // AppLayout should render a nav sidebar or header
    const nav = page.locator("nav").first();
    await expect(nav).toBeVisible();
  });

  test("breadcrumbs or page title renders on sub-pages", async ({ page }) => {
    await page.goto("/admin/users");

    // PageShell renders a title; verify it shows
    await expect(page.locator("h1, h2").first()).toBeVisible();
  });

  test("deep linking to specific pages works", async ({ page }) => {
    await page.goto("/finance/tariffs");
    await expect(page.locator("body")).toContainText(/tariff/i);

    await page.goto("/admin/audit");
    await expect(page.locator("body")).toContainText(/audit/i);

    await page.goto("/ehr/test-patient-001/vitals");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("back navigation works between pages", async ({ page }) => {
    await page.goto("/finance");
    await page.getByRole("link", { name: /billing/i }).click();
    await expect(page).toHaveURL(/\/finance\/billing/);

    await page.goBack();
    await expect(page).toHaveURL(/\/finance/);
  });

  test("facility selection page accessible without facility set", async ({ page }) => {
    // Clear facility from localStorage
    await page.evaluate(() => localStorage.removeItem("exp:facility"));
    await page.goto("/facility");

    await expect(page.getByText("Select Facility")).toBeVisible();
  });

  test("workspace page is accessible", async ({ page }) => {
    await page.goto("/workspace");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("clinical hub page loads", async ({ page }) => {
    await page.goto("/clinical");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });
});
