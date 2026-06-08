import { test, expect } from "@playwright/test";

async function seedCitizenSession(page: import("@playwright/test").Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem(
      "exp:auth_user",
      JSON.stringify({
        id: "cit-shell-1",
        email: "citizen@impilo.zw",
        displayName: "Citizen Shell",
        roles: ["CITIZEN"],
        actorType: "CITIZEN",
        providerActivated: false,
      }),
    );
    sessionStorage.setItem("exp:auth_token", "e2e-shell-token");
    document.cookie = "exp_has_session=1; path=/";
  });
}

test.describe("Nompilo shell entry (taskbar only)", () => {
  test("authenticated home shows taskbar Nompilo, not floating assist chrome", async ({ page }) => {
    await seedCitizenSession(page);
    await page.goto("/home");

    await expect(page.getByLabel(/open nompilo ask/i)).toBeVisible();
    await expect(page.getByTitle("Nompilo — contextual help")).toHaveCount(0);
    await expect(page.getByTitle(/Proactive|Assistant notifications/i)).toHaveCount(0);
  });

  test("auth login uses NompiloHint only (no taskbar chrome)", async ({ page }) => {
    await page.goto("/auth/login");
    await expect(page.getByLabel(/open nompilo ask/i)).toHaveCount(0);
    await expect(page.locator("body")).toContainText(/Nompilo|assistant|help/i);
  });
});
