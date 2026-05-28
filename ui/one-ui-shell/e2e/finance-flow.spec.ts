import { test, expect } from "./fixtures";

test.describe("Finance workflow", () => {
  test("finance dashboard loads with section cards", async ({ page }) => {
    await page.goto("/finance");

    await expect(page.getByText("Finance Dashboard")).toBeVisible();
    await expect(page.getByText("Financial management and revenue cycle")).toBeVisible();
  });

  test("finance dashboard shows all four sections", async ({ page }) => {
    await page.goto("/finance");

    await expect(page.getByText("Billing")).toBeVisible();
    await expect(page.getByText("Claims")).toBeVisible();
    await expect(page.getByText("Payments")).toBeVisible();
    await expect(page.getByText("Tariffs")).toBeVisible();
  });

  test("billing page is accessible from finance dashboard", async ({ page }) => {
    await page.goto("/finance");

    await page.getByRole("link", { name: /billing/i }).click();
    await expect(page).toHaveURL(/\/finance\/billing/);
  });

  test("billing page renders", async ({ page }) => {
    await page.goto("/finance/billing");

    await expect(page.locator("body")).toContainText(/billing|invoice/i);
  });

  test("claims page renders", async ({ page }) => {
    await page.goto("/finance/claims");

    await expect(page.locator("body")).toContainText(/claim/i);
  });

  test("payments page is accessible", async ({ page }) => {
    await page.goto("/finance/payments");

    await expect(page.locator("body")).toContainText(/payment/i);
  });

  test("tariffs management page loads", async ({ page }) => {
    await page.goto("/finance/tariffs");

    await expect(page.locator("body")).toContainText(/tariff/i);
  });

  test("navigation between finance sections works", async ({ page }) => {
    await page.goto("/finance");

    await page.getByRole("link", { name: /claims/i }).click();
    await expect(page).toHaveURL(/\/finance\/claims/);

    await page.goBack();
    await expect(page).toHaveURL(/\/finance/);
  });
});
