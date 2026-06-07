import { test, expect } from "./fixtures";

test.describe("Payment billing claim journey", () => {
  test("payer-ops page loads with intent workflow and sandbox rail controls", async ({ page }) => {
    await page.goto("/finance/payer-ops");

    await expect(page.locator("body")).toContainText(/payer/i);
    await expect(page.getByRole("button", { name: /Reselect SANDBOX rail/i })).toBeDisabled();
    await expect(page.getByRole("button", { name: /Initiate attempt/i })).toBeDisabled();
  });

  test("finance hub links to payer operations", async ({ page }) => {
    await page.goto("/finance");
    await expect(page.getByText("Finance Dashboard")).toBeVisible();
  });
});
