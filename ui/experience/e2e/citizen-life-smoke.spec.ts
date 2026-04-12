import { test as base, expect } from "./fixtures";

/**
 * Session keys must match `useHydration` / `api-client` (not legacy `localStorage` `exp:auth` alone).
 */
const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript(() => {
      sessionStorage.setItem("exp:auth_token", "e2e-mock-token");
      sessionStorage.setItem(
        "exp:auth_user",
        JSON.stringify({
          id: "e2e-provider-001",
          email: "e2e@impilo.zw",
          displayName: "Dr. E2E Test",
          roles: ["CLINICAL", "ADMIN", "FINANCE", "DISPENSER"],
          actorType: "PROVIDER",
        }),
      );
    });
    await use(page);
  },
});

/** Must match {@code NEXT_PUBLIC_BFF_URL} default in `src/lib/api-client.ts`. */
const BFF_ORIGIN = process.env.PLAYWRIGHT_BFF_URL || "http://localhost:8160";

test.describe("Citizen life smoke (BFF mocked)", () => {
  test.beforeEach(async ({ page }) => {
    await page.context().addCookies([
      { name: "exp_has_session", value: "1", path: "/", domain: "localhost" },
    ]);
    await page.route(
      (url) =>
        url.origin === new URL(BFF_ORIGIN).origin &&
        url.pathname.startsWith("/internal/v1/mobile/citizen/wellness"),
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: [] }),
        });
      },
    );
    await page.route(
      (url) =>
        url.origin === new URL(BFF_ORIGIN).origin && url.pathname === "/internal/v1/commerce/cart",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ data: [] }),
        });
      },
    );
  });

  test("wellness hub renders and citizen wellness GET is issued", async ({ page }) => {
    const wellnessCalls: string[] = [];
    page.on("request", (req) => {
      const u = req.url();
      if (u.includes("/internal/v1/mobile/citizen/wellness/activities")) {
        wellnessCalls.push(u);
      }
    });

    await page.goto("/wellness");
    await expect(page.getByRole("heading", { name: "Wellness Hub" })).toBeVisible();
    await expect.poll(() => wellnessCalls.length).toBeGreaterThan(0);
    expect(wellnessCalls[0]).toContain("patientId=e2e-provider-001");
  });

  test("monitoring devices page renders", async ({ page }) => {
    await page.goto("/monitoring/devices");
    await expect(page.getByRole("heading", { name: "My Devices" })).toBeVisible();
    await expect(page.getByText("No devices paired")).toBeVisible();
  });

  test("marketplace cart renders empty state with mocked cart API", async ({ page }) => {
    await page.goto("/marketplace/cart");
    await expect(page.getByRole("heading", { name: "Shopping Cart" })).toBeVisible();
    await expect(page.getByText("Your cart is empty")).toBeVisible();
  });
});

export { expect };
