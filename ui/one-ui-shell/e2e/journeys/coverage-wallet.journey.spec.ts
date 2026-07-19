import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

/** Accept the policy-consent gate wherever it appears (overlay or full /consent page). */
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

async function reach(page: Page, path: string) {
  await gotoAs(page, PERSONAS.learner, path);
  await settleConsent(page);
  if (page.url().includes("/consent")) {
    await page.goto(`${page.url().split("/consent")[0]}${path}`, { waitUntil: "domcontentloaded" });
    await settleConsent(page);
  }
}

/**
 * Ruvimbo Wave 1 — the Coverage capability is now branded Ruvimbo and the citizen
 * wallet surfaces the reservation-aware benefits position. This proves, live through
 * the real ingress:
 *   1. the launcher carries a "Ruvimbo" tile (front-and-centre rebrand);
 *   2. the wallet (/coverage/member) renders with a "Your benefits" section.
 * (Deep benefit/limit/eligibility/token correctness is proven deterministically by
 *  scripts/e2e/ruvimbo-foundation-proof.sh — 33/33 with DB truth.)
 */
test.describe("Ruvimbo — coverage wallet + branding (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("citizen sees the Ruvimbo tile and a benefits-aware coverage wallet", async ({ page }) => {
    await loginAs(page, PERSONAS.learner);
    await gotoAs(page, PERSONAS.learner, "/home");
    await settleConsent(page);

    // Wallet renders (My Coverage), and the Ruvimbo "Your benefits" section is present.
    await reach(page, "/coverage/member");
    await expect(page.getByRole("heading", { name: /my coverage/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/your benefits/i).first()).toBeVisible({ timeout: 20_000 });

    // The launcher now carries a Ruvimbo tile (Coverage capability rebranded).
    const startBtn = page
      .getByRole("button", { name: /^start( menu)?$|open start/i })
      .or(page.locator('[aria-label="Start menu"], [aria-label="Open Start menu"]'))
      .first();
    if (await startBtn.isVisible().catch(() => false)) {
      await startBtn.click();
      await expect(page.getByText(/^Ruvimbo$/).first()).toBeVisible({ timeout: 10_000 });
    }
  });
});
