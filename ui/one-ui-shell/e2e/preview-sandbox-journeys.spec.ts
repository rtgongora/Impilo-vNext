import { test, expect } from "@playwright/test";
import {
  RUN_PREVIEW,
  authenticatePreviewPage,
  skipUnlessAuthenticated,
  gotoAppPath,
  bffGetFromBrowser,
} from "./preview-sandbox-helpers";

/** Cross-service journey live proofs against preview ingress (read-path + route reachability). */
const JOURNEY_PROBES: Array<{
  id: string;
  route: string;
  bffGet: string;
  label: RegExp;
  /** When true, the BFF read-path must return 2xx with a `data` envelope (real read-path),
   * not merely a non-5xx status. Endpoints requiring a mandatory filter stay reachability-only. */
  expectData?: boolean;
}> = [
  {
    id: "telemedicine-to-pct",
    route: "/telemedicine/session",
    // Requires a patientId/referrerId filter; reachability + non-5xx is the proof here.
    bffGet: "/internal/v1/teleconsult/sessions?page=0&size=5",
    label: /telemedicine|session|consult/i,
  },
  {
    id: "orders-inventory-labs-imaging",
    route: "/pharmacy",
    bffGet: "/internal/v1/pharmacy/orders?page=0&size=5",
    label: /pharmacy|medication|dispense/i,
    expectData: true,
  },
  {
    id: "costing-billing-payments",
    route: "/finance/payer-ops",
    bffGet: "/internal/v1/finance/payer-ops/summary",
    label: /finance|payer|billing|claims/i,
    expectData: true,
  },
  {
    id: "marketplace-procurement",
    route: "/enterprise",
    bffGet: "/internal/v1/procurement/requisitions?page=0&size=5",
    label: /enterprise|procurement|inventory/i,
    expectData: true,
  },
  {
    id: "public-health-surveillance",
    route: "/public-health",
    bffGet: "/internal/v1/public-health/programmes?page=0&size=5",
    label: /public health|surveillance|programme/i,
    expectData: true,
  },
  {
    id: "blood-services-chain",
    route: "/blood",
    bffGet: "/internal/v1/blood/inventory?page=0&size=5",
    label: /blood|transfusion|donor/i,
    expectData: true,
  },
];

test.describe("Preview sandbox cross-service journey proofs", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });
  test.setTimeout(120_000);

  for (const journey of JOURNEY_PROBES) {
    test(`${journey.id}: route + BFF read path`, async ({ page }) => {
      skipUnlessAuthenticated(await authenticatePreviewPage(page));
      await gotoAppPath(page, journey.route);
      await expect(page.locator("body")).toBeVisible();
      await expect(page.locator("body")).not.toContainText(/502 Bad Gateway|503 Service Unavailable/i);

      const get = await bffGetFromBrowser(page, journey.bffGet);
      expect(
        get.status,
        `${journey.id} BFF GET ${journey.bffGet} must not 5xx (got ${get.status})`,
      ).toBeLessThan(500);

      if (journey.expectData) {
        // Real read-path: must be 2xx and carry a `data` envelope from the live upstream.
        expect(
          get.status,
          `${journey.id} BFF GET ${journey.bffGet} must be 2xx (got ${get.status}): ${get.text?.slice(0, 200)}`,
        ).toBeGreaterThanOrEqual(200);
        expect(get.status).toBeLessThan(300);
        let parsed: unknown;
        try {
          parsed = JSON.parse(get.text);
        } catch {
          parsed = null;
        }
        expect(
          parsed && typeof parsed === "object" && "data" in (parsed as Record<string, unknown>),
          `${journey.id} BFF GET ${journey.bffGet} must return a data envelope: ${get.text?.slice(0, 200)}`,
        ).toBeTruthy();
      }

      // Poll for rendered content: SPA routes (e.g. /enterprise) hydrate after
      // domcontentloaded, so a single innerText read can race the render.
      await expect
        .poll(async () => (await page.locator("body").innerText()).length, { timeout: 15_000 })
        .toBeGreaterThan(20);
    });
  }
});
