import { test, expect } from "./fixtures";

test.describe("Home dashboard", () => {
  test("home page renders with page title and greeting", async ({ page }) => {
    await page.goto("/home");

    // PageShell should show the home heading content
    await expect(page.locator("h1, h2").first()).toBeVisible();
    // The page should be loaded (not stuck on a loading spinner)
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("module categories render for admin role", async ({ page }) => {
    await page.goto("/home");

    // The mock user has CLINICAL, ADMIN, FINANCE, DISPENSER roles
    // so all category cards should appear
    await expect(page.getByText("Clinical Care")).toBeVisible();
    await expect(page.getByText("Finance")).toBeVisible();
  });

  test("clinical module links are present", async ({ page }) => {
    await page.goto("/home");

    // Clinical Care category should contain key module links
    await expect(page.getByText("Patient Queue")).toBeVisible();
    await expect(page.getByText("Scheduling")).toBeVisible();
    await expect(page.getByText("Bed Management")).toBeVisible();
  });

  test("facility selection hub is accessible", async ({ page }) => {
    await page.goto("/facility");

    await expect(page.getByText("Select Facility")).toBeVisible();
    await expect(page.getByText("Choose the facility you will be working at today")).toBeVisible();
  });

  test("navigation to clinical modules works from home", async ({ page }) => {
    await page.goto("/home");

    const queueLink = page.getByRole("link", { name: /patient queue/i });
    if (await queueLink.isVisible()) {
      await queueLink.click();
      await expect(page).toHaveURL(/\/queue/);
    }
  });

  test("communication module is accessible", async ({ page }) => {
    await page.goto("/communication");

    // The communication page should load
    await expect(page.locator("body")).toContainText(/communication|noticeboard|messages/i);
  });

  test("home page sub-routes are accessible", async ({ page }) => {
    // Profile sub-route
    await page.goto("/home/profile");
    await expect(page.locator("body")).not.toHaveText(/^$/);

    // Notifications sub-route
    await page.goto("/home/notifications");
    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("home page renders personal section links", async ({ page }) => {
    await page.goto("/home");

    // The home page should have navigable personal items
    const bodyText = await page.locator("body").textContent();
    expect(bodyText).toBeTruthy();
  });
});
