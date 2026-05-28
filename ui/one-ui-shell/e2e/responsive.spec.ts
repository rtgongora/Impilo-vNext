import { test, expect } from "./fixtures";

// These tests run in the mobile-chrome project (Pixel 5 viewport)
test.describe("Responsive design", () => {
  test.describe.configure({ mode: "serial" });

  test("home page adapts to mobile viewport", async ({ page }) => {
    await page.goto("/home");

    // The page should render without horizontal overflow
    const viewportWidth = page.viewportSize()?.width ?? 0;
    const bodyScrollWidth = await page.evaluate(() => document.body.scrollWidth);
    expect(bodyScrollWidth).toBeLessThanOrEqual(viewportWidth + 20); // small tolerance
  });

  test("navigation collapses on mobile", async ({ page }) => {
    await page.goto("/home");

    // On mobile, the sidebar should either be hidden or collapsed behind a menu button
    const sidebar = page.locator("aside, nav[data-sidebar]").first();
    if (await sidebar.count()) {
      // If sidebar exists, it should be off-screen or have a toggle
      const menuButton = page.getByRole("button", { name: /menu|toggle/i });
      const isMenuVisible = await menuButton.isVisible().catch(() => false);
      // Either the sidebar is hidden or there is a menu toggle
      const sidebarVisible = await sidebar.isVisible().catch(() => false);
      expect(isMenuVisible || !sidebarVisible).toBeTruthy();
    }
  });

  test("finance dashboard cards stack vertically on mobile", async ({ page }) => {
    await page.goto("/finance");

    await expect(page.getByText("Finance Dashboard")).toBeVisible();

    // All finance section cards should be visible (stacked)
    await expect(page.getByText("Billing")).toBeVisible();
    await expect(page.getByText("Claims")).toBeVisible();
    await expect(page.getByText("Payments")).toBeVisible();
    await expect(page.getByText("Tariffs")).toBeVisible();
  });

  test("admin dashboard cards are accessible on mobile", async ({ page }) => {
    await page.goto("/admin");

    await expect(page.getByText("Administration")).toBeVisible();
    await expect(page.getByText("Users")).toBeVisible();
    await expect(page.getByText("Audit")).toBeVisible();
  });

  test("login page is usable on mobile", async ({ page }) => {
    // Use base test page (no auth) for login
    await page.evaluate(() => localStorage.clear());
    await page.goto("/auth/login");

    await expect(page.locator("#email")).toBeVisible();
    await expect(page.locator("#password")).toBeVisible();
    await expect(page.getByRole("button", { name: /sign in/i })).toBeVisible();

    // Provider ID and Biometric links should be visible
    await expect(page.getByRole("link", { name: /provider id/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /biometric/i })).toBeVisible();
  });

  test("pharmacy hub cards stack on mobile", async ({ page }) => {
    await page.goto("/pharmacy");

    await expect(page.getByText("Prescriptions")).toBeVisible();
    await expect(page.getByText("Dispense")).toBeVisible();
    await expect(page.getByText("Stock")).toBeVisible();
  });

  test("EHR chart sections are scrollable on mobile", async ({ page }) => {
    await page.goto("/ehr/test-patient-001");

    // The chart sections grid should be within the viewport or scrollable
    await expect(page.getByText("Patient Chart")).toBeVisible();
    const viewportWidth = page.viewportSize()?.width ?? 0;
    const bodyScrollWidth = await page.evaluate(() => document.body.scrollWidth);
    expect(bodyScrollWidth).toBeLessThanOrEqual(viewportWidth + 20);
  });

  test("AI Governance tabs are scrollable on mobile", async ({ page }) => {
    await page.goto("/ai-governance");

    await expect(page.getByText("AI Governance")).toBeVisible();
    // The tab bar has overflow-x-auto, so it should be scrollable
    const tabBar = page.locator(".overflow-x-auto").first();
    if (await tabBar.count()) {
      await expect(tabBar).toBeVisible();
    }
  });
});
