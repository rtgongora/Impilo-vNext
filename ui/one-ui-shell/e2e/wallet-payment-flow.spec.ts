import { test, expect } from "./fixtures";

test.describe("Wallet payment journey", () => {
  test("wallet hub loads without auth redirect", async ({ page }) => {
    await page.goto("/wallet");
    await expect(page.getByText(/wallet/i).first()).toBeVisible();
  });

  test("send money page exposes transfer and merchant modes", async ({ page }) => {
    await page.goto("/wallet/send");

    await expect(page.getByText("Send Money")).toBeVisible();
    await expect(page.getByText(/transfer funds or pay a merchant/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /transfer/i }).first()).toBeVisible();
  });

  test("transactions history route renders", async ({ page }) => {
    await page.goto("/wallet/transactions");
    await expect(page.locator("body")).toContainText(/transaction/i);
  });
});
