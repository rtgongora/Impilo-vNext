import { test, expect } from "./fixtures";

test.describe("Lab order hub and worklist flow", () => {
  test("lab order hub loads with type filters and section cards", async ({ page }) => {
    await page.goto("/lab");

    await expect(page.getByRole("heading", { name: "Order Hub" })).toBeVisible();
    await expect(page.getByText(/multi-type order orchestration/i)).toBeVisible();
    await expect(page.getByRole("button", { name: "Laboratory" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Imaging" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Pharmacy" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Blood" })).toBeVisible();
  });

  test("lab hub links to worklist, results, catalog, and reconciliation", async ({ page }) => {
    await page.goto("/lab");

    await expect(page.getByRole("link", { name: /Worklist/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /Results Review/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /Test Catalog/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /Reconciliation/i })).toBeVisible();
  });

  test("worklist page is accessible from lab hub", async ({ page }) => {
    await page.goto("/lab");

    await page.getByRole("link", { name: /Worklist/i }).click();
    await expect(page).toHaveURL(/\/lab\/worklist/);
    await expect(page.getByRole("heading", { name: "Lab Worklist" })).toBeVisible();
  });

  test("catalog page renders with live maturity badge", async ({ page }) => {
    await page.goto("/lab/catalog");

    await expect(page.getByRole("heading", { name: "Test Catalog" })).toBeVisible();
    await expect(page.locator("body")).toContainText(/lab-catalog/i);
  });

  test("reconciliation page renders with live maturity badge", async ({ page }) => {
    await page.goto("/lab/reconciliation");

    await expect(page.getByRole("heading", { name: "Lab Reconciliation" })).toBeVisible();
    await expect(page.locator("body")).toContainText(/lab-reconciliation/i);
  });

  test("order type filter switches on lab hub", async ({ page }) => {
    await page.goto("/lab");

    await page.getByRole("button", { name: "Imaging" }).click();
    await expect(page.getByRole("button", { name: "Imaging" })).toHaveClass(/bg-violet-600/);

    const worklistLink = page.getByRole("link", { name: /Worklist/i });
    await expect(worklistLink).toHaveAttribute("href", /type=IMAGING/);
  });

  test("navigation between lab sections works", async ({ page }) => {
    await page.goto("/lab/worklist");
    await expect(page).toHaveURL(/\/lab\/worklist/);

    await page.goto("/lab/catalog");
    await expect(page).toHaveURL(/\/lab\/catalog/);

    await page.goto("/lab/reconciliation");
    await expect(page).toHaveURL(/\/lab\/reconciliation/);

    await page.goBack();
    await expect(page).toHaveURL(/\/lab\/catalog/);
  });
});
