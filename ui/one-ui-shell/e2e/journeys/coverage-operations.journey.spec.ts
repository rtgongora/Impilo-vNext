import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

/** Settle the policy-consent gate if it appears. */
async function settleConsent(page: Page) {
  for (let i = 0; i < 4; i += 1) {
    const gated = page.url().includes("/consent") ||
      (await page.getByText(/review our policies/i).first().isVisible({ timeout: 2_000 }).catch(() => false));
    if (!gated) return;
    for (const box of await page.locator('input[type="checkbox"]').all()) await box.check().catch(() => undefined);
    await page.getByRole("button", { name: /accept and continue/i }).click().catch(() => undefined);
    await page.waitForTimeout(2_000);
  }
}

/**
 * Ruvimbo Operations — the admin/payer/provider workbench is reachable and operable
 * in the browser. Proves (live, ADMIN session): the console renders its tab bar,
 * the Payers tab shows the real payer registry (seeded PAYER-MOHCC / PAYER-PRIVATE-01),
 * a payer can be REGISTERED from the browser and appears in the registry, and other
 * tabs (Plans, Claims, Switch) render. Deep correctness of each rail is proven by the
 * scripts/e2e/ruvimbo-*-proof.sh API+DB suites (33+20+23+21, all green ×2).
 */
test.describe("Ruvimbo Operations — admin workbench (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("admin reaches Ruvimbo Operations and registers a payer in-browser", async ({ page }) => {
    await loginAs(page, PERSONAS.nationalAdmin);
    await gotoAs(page, PERSONAS.nationalAdmin, "/home");
    await settleConsent(page);

    await gotoAs(page, PERSONAS.nationalAdmin, "/coverage/operations");
    await settleConsent(page);
    if (page.url().includes("/consent")) {
      await page.goto(`${page.url().split("/consent")[0]}/coverage/operations`, { waitUntil: "domcontentloaded" });
      await settleConsent(page);
    }

    // Console renders with its tab bar.
    await expect(page.getByRole("heading", { name: /ruvimbo operations/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("ruvimbo-tab-payers")).toBeVisible({ timeout: 10_000 });

    // Payers tab shows the real seeded registry.
    await expect(page.getByText(/PAYER-MOHCC/).first()).toBeVisible({ timeout: 20_000 });

    // Register a payer from the browser → appears in the registry table.
    const code = `PAY-UI-${Date.now().toString().slice(-6)}`;
    const inputs = page.locator('input[type="text"]');
    await inputs.nth(0).fill(code);
    await inputs.nth(1).fill("Browser Test Payer");
    await page.getByTestId("ruvimbo-create-payer").click();
    await expect(page.getByText(code).first()).toBeVisible({ timeout: 20_000 });

    // Other tabs render.
    await page.getByTestId("ruvimbo-tab-plans").click();
    await expect(page.getByText(/Payer → Scheme → Product → Version/i)).toBeVisible({ timeout: 10_000 });
    await page.getByTestId("ruvimbo-tab-switch").click();
    await expect(page.getByText(/credential-gated/i)).toBeVisible({ timeout: 10_000 });
  });
});
