import { test, expect } from "@playwright/test";
import {
  PREVIEW_ORIGIN,
  RUN_PREVIEW,
  installPreviewSession,
  gotoAppPath,
  bffGetFromBrowser,
} from "./preview-sandbox-helpers";

/** Cross-service journey live proofs against preview ingress (read-path + route reachability). */
const JOURNEY_PROBES: Array<{
  id: string;
  route: string;
  bffGet: string;
  label: RegExp;
}> = [
  {
    id: "telemedicine-to-pct",
    route: "/telemedicine/session",
    bffGet: "/internal/v1/teleconsult/sessions?page=0&size=5",
    label: /telemedicine|session|consult/i,
  },
  {
    id: "orders-inventory-labs-imaging",
    route: "/pharmacy",
    bffGet: "/internal/v1/pharmacy/orders?page=0&size=5",
    label: /pharmacy|medication|dispense/i,
  },
  {
    id: "costing-billing-payments",
    route: "/finance/payer-ops",
    bffGet: "/internal/v1/finance/payer-ops/summary",
    label: /finance|payer|billing|claims/i,
  },
  {
    id: "marketplace-procurement",
    route: "/enterprise",
    bffGet: "/internal/v1/procurement/requisitions?page=0&size=5",
    label: /enterprise|procurement|inventory/i,
  },
  {
    id: "public-health-surveillance",
    route: "/public-health",
    bffGet: "/internal/v1/public-health/programmes?page=0&size=5",
    label: /public health|surveillance|programme/i,
  },
  {
    id: "blood-services-chain",
    route: "/blood",
    bffGet: "/internal/v1/blood/inventory?page=0&size=5",
    label: /blood|transfusion|donor/i,
  },
];

test.describe("Preview sandbox cross-service journey proofs", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test.beforeEach(async ({ context, baseURL }) => {
    await installPreviewSession(context, baseURL ?? PREVIEW_ORIGIN);
  });

  for (const journey of JOURNEY_PROBES) {
    test(`${journey.id}: route + BFF read path`, async ({ page }) => {
      await gotoAppPath(page, journey.route);
      await expect(page.locator("body")).toBeVisible();
      await expect(page.locator("body")).not.toContainText(/502 Bad Gateway|503 Service Unavailable/i);

      const get = await bffGetFromBrowser(page, journey.bffGet);
      expect(
        get.status,
        `${journey.id} BFF GET ${journey.bffGet} must not 5xx (got ${get.status})`,
      ).toBeLessThan(500);

      const bodyText = await page.locator("body").innerText();
      expect(bodyText.length).toBeGreaterThan(20);
    });
  }
});
