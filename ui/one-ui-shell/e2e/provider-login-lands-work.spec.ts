import { test, expect } from "@playwright/test";

async function seedActivatedProviderSession(page: import("@playwright/test").Page) {
  await page.addInitScript(() => {
    const user = {
      id: "prov-work-1",
      email: "work@impilo.zw",
      displayName: "Dr Work Provider",
      roles: ["CLINICIAN"],
      actorType: "PROVIDER",
      assuranceLevel: "VERIFIED",
      providerActivated: true,
      providerId: "PRV-WORK-001",
    };
    sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
    sessionStorage.setItem("exp:auth_token", "e2e-work-token");
    sessionStorage.setItem("exp:provider_id", user.providerId);
    sessionStorage.setItem(
      "exp:facility",
      JSON.stringify({
        id: "fac-parirenyatwa",
        name: "Parirenyatwa Hospital",
        code: "PAR-001",
        facilityType: "HOSPITAL",
      }),
    );
  });
}

test.describe("Provider login lands on work surface", () => {
  test("provider path resolves to provider-workspace", async ({ page }) => {
    await seedActivatedProviderSession(page);

    await page.route("**/internal/v1/identity/linked-ids", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "linked-work",
            type: "linked-ids",
            attributes: { providerId: "PRV-WORK-001" },
          },
        }),
      });
    });

    await page.goto("/auth/resolving?returnTo=%2Fprovider-workspace");
    await expect(page).toHaveURL(/\/provider-workspace/, { timeout: 12_000 });
    await expect(page.locator("body")).toContainText(/provider|work|workspace/i);
  });

  test("provider-workspace route is reachable with seeded session", async ({ page }) => {
    await seedActivatedProviderSession(page);
    await page.goto("/provider-workspace");
    await expect(page).toHaveURL(/\/provider-workspace/);
    await expect(page.locator("body")).not.toBeEmpty();
  });
});
