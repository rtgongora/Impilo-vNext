import { test, expect } from "./fixtures";

test.describe("Coverage enrollment journey", () => {
  test("enroll page loads with three-step workflow", async ({ page }) => {
    await page.goto("/coverage/enroll");

    await expect(page.getByText("Enroll in coverage")).toBeVisible();
    await expect(page.getByText("1. Choose a plan")).toBeVisible();
    await expect(page.getByText("2. Eligibility check")).toBeVisible();
    await expect(page.getByText("3. Confirm enrollment")).toBeVisible();
  });

  test("enroll page exposes eligibility and member enrollment actions", async ({ page }) => {
    await page.goto("/coverage/enroll");

    await expect(page.getByRole("button", { name: /run eligibility/i })).toBeVisible();
    await expect(page.getByPlaceholder("Member number")).toBeVisible();
    await expect(page.getByRole("button", { name: /enroll member/i })).toBeDisabled();
  });

  test("coverage hub links back from enroll", async ({ page }) => {
    await page.goto("/coverage/enroll");
    await expect(page.getByRole("link", { name: /back to coverage/i })).toHaveAttribute("href", "/coverage");
  });
});
