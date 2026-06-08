import { test, expect } from "./fixtures";

test.describe("Consent capture journey", () => {
  test("mvumo registry admin exposes template and consent-request workflows", async ({ page }) => {
    await page.goto("/registry/mvumo");

    await expect(page.getByText("Mvumo")).toBeVisible();
    await expect(page.getByText("Template governance")).toBeVisible();
    await expect(page.getByText("Consent request initiation")).toBeVisible();
    await expect(page.getByRole("button", { name: /Create template/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Create consent request/i })).toBeDisabled();
  });
});
