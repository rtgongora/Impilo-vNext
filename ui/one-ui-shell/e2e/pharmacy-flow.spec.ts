import { test, expect } from "./fixtures";

test.describe("Pharmacy workflow", () => {
  test("pharmacy dashboard loads with section cards", async ({ page }) => {
    await page.goto("/pharmacy");

    await expect(page.getByText("Pharmacy")).toBeVisible();
    await expect(page.getByText("Manage prescriptions, dispensing, and stock")).toBeVisible();
  });

  test("pharmacy shows prescriptions, dispense, and stock sections", async ({ page }) => {
    await page.goto("/pharmacy");

    await expect(page.getByText("Prescriptions")).toBeVisible();
    await expect(page.getByText("Dispense")).toBeVisible();
    await expect(page.getByText("Stock")).toBeVisible();
  });

  test("prescriptions page is accessible from pharmacy hub", async ({ page }) => {
    await page.goto("/pharmacy");

    await page.getByRole("link", { name: /prescriptions/i }).click();
    await expect(page).toHaveURL(/\/pharmacy\/prescriptions/);
  });

  test("prescriptions list page renders", async ({ page }) => {
    await page.goto("/pharmacy/prescriptions");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("stock management page shows inventory content", async ({ page }) => {
    await page.goto("/pharmacy/stock");

    await expect(page.locator("body")).toContainText(/stock|inventory/i);
  });

  test("dispense page is accessible", async ({ page }) => {
    await page.goto("/pharmacy/dispense");

    await expect(page.locator("body")).not.toHaveText(/^$/);
  });

  test("navigation between pharmacy sections works", async ({ page }) => {
    await page.goto("/pharmacy");

    // Navigate to stock
    await page.getByRole("link", { name: /stock/i }).click();
    await expect(page).toHaveURL(/\/pharmacy\/stock/);

    // Navigate back (browser back)
    await page.goBack();
    await expect(page).toHaveURL(/\/pharmacy/);
  });
});
