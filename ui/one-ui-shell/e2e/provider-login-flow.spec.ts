import { test, expect } from "@playwright/test";
import { resolvePostLoginDestination } from "../src/lib/resolve-post-login-destination";

const LINKED_IDS_FIXTURE = {
  data: {
    id: "linked-e2e",
    type: "linked-ids",
    attributes: {},
  },
};

async function seedAuthSession(
  page: import("@playwright/test").Page,
  user: Record<string, unknown>,
  facility?: Record<string, unknown> | null,
) {
  await page.addInitScript(
    ({ authUser, facilityPayload }) => {
      sessionStorage.setItem("exp:auth_user", JSON.stringify(authUser));
      sessionStorage.setItem("exp:auth_token", "e2e-test-token");
      if (authUser.providerId) {
        sessionStorage.setItem("exp:provider_id", String(authUser.providerId));
      }
      if (facilityPayload) {
        sessionStorage.setItem("exp:facility", JSON.stringify(facilityPayload));
      } else {
        sessionStorage.removeItem("exp:facility");
      }
    },
    { authUser: user, facilityPayload: facility ?? null },
  );
}

async function mockLinkedIdsRoute(page: import("@playwright/test").Page, attributes: Record<string, unknown> = {}) {
  await page.route("**/internal/v1/identity/linked-ids", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          id: "linked-e2e",
          type: "linked-ids",
          attributes,
        },
      }),
    });
  });
}

test.describe("Provider login and role activation", () => {
  test("provider activate page loads activation rail", async ({ page }) => {
    await page.goto("/provider/activate");
    await expect(page.locator("body")).toContainText(/Activate Provider Role|provider/i);
  });

  test("auth resolving route is reachable", async ({ page }) => {
    await page.goto("/auth/resolving");
    await expect(page.locator("body")).toContainText(/Preparing your experience|identity/i);
  });
});

test.describe("Provider login resolving destinations", () => {
  test("resolvePostLoginDestination matrix matches product rules", async () => {
    expect(
      resolvePostLoginDestination({
        user: {
          actorType: "PROVIDER",
          roles: ["CLINICIAN"],
          providerActivated: true,
          providerId: "PRV-E2E",
        },
        hasFacility: true,
      }).href,
    ).toBe("/provider-workspace");

    expect(
      resolvePostLoginDestination({
        user: {
          actorType: "CITIZEN",
          roles: ["CLINICIAN"],
          providerActivated: false,
        },
        linkedProviderId: "PRV-LINKED",
      }).href,
    ).toBe("/provider/activate?returnTo=%2Fprovider-workspace");

    expect(
      resolvePostLoginDestination({
        user: {
          actorType: "CITIZEN",
          roles: ["CITIZEN"],
          providerActivated: false,
        },
      }).href,
    ).toBe("/home");
  });

  test("activated provider with facility lands on provider-workspace after resolving", async ({ page }) => {
    await seedAuthSession(
      page,
      {
        id: "prov-user-1",
        email: "provider@impilo.zw",
        displayName: "Dr E2E Provider",
        roles: ["CLINICIAN"],
        actorType: "PROVIDER",
        assuranceLevel: "VERIFIED",
        providerActivated: true,
        providerId: "PRV-E2E-001",
      },
      {
        id: "fac-harare-central",
        name: "Harare Central Hospital",
        code: "HCH-001",
      },
    );
    await mockLinkedIdsRoute(page);

    await page.goto("/auth/resolving");
    await expect(page).toHaveURL(/\/provider-workspace/, { timeout: 12_000 });
  });

  test("activated provider without facility lands on facility picker with returnTo", async ({ page }) => {
    await seedAuthSession(page, {
      id: "prov-no-fac",
      email: "nofac@impilo.zw",
      displayName: "Provider No Facility",
      roles: ["CLINICIAN"],
      actorType: "PROVIDER",
      assuranceLevel: "VERIFIED",
      providerActivated: true,
      providerId: "PRV-NO-FAC",
    });
    await mockLinkedIdsRoute(page);

    await page.goto("/auth/resolving");
    await expect(page).toHaveURL(/\/facility\?returnTo=%2Fprovider-workspace/, { timeout: 12_000 });
  });

  test("citizen without linked provider lands on home with my_life mode after resolving", async ({ page }) => {
    await seedAuthSession(page, {
      id: "cit-user-1",
      email: "citizen@impilo.zw",
      displayName: "Citizen E2E",
      roles: ["CITIZEN"],
      actorType: "CITIZEN",
      assuranceLevel: "VERIFIED",
      providerActivated: false,
    });
    await mockLinkedIdsRoute(page, LINKED_IDS_FIXTURE.data.attributes);

    await page.goto("/auth/resolving");
    await expect(page).toHaveURL(/\/home/, { timeout: 12_000 });

    const mode = await page.evaluate(() => sessionStorage.getItem("exp:operational_mode"));
    const homeMode = await page.getByTestId("home-operational-mode").textContent().catch(() => null);
    expect(mode === "my_life" || homeMode === "my_life").toBeTruthy();
  });

  test("linked-but-inactive provider lands on activation after resolving", async ({ page }) => {
    await seedAuthSession(page, {
      id: "prov-link-1",
      email: "linked@impilo.zw",
      displayName: "Linked Provider",
      roles: ["CLINICIAN"],
      actorType: "CITIZEN",
      assuranceLevel: "VERIFIED",
      providerActivated: false,
    });
    await mockLinkedIdsRoute(page, { providerId: "PRV-LINKED-001" });

    await page.goto("/auth/resolving");
    await expect(page).toHaveURL(/\/provider\/activate\?returnTo=%2Fprovider-workspace/, {
      timeout: 12_000,
    });
  });
});
