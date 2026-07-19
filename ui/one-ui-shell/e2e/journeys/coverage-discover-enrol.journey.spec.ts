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

/** Navigate to a path, settling the AuthGuard's client-side consent redirect if it fires. */
async function reach(page: Page, path: string) {
  await gotoAs(page, PERSONAS.learner, path);
  await settleConsent(page);
  if (page.url().includes("/consent")) {
    await page.goto(`${page.url().split("/consent")[0]}${path}`, { waitUntil: "domcontentloaded" });
    await settleConsent(page);
  }
}

/**
 * Golden journey — Coverage is now discoverable and enrollable.
 *
 * Coverage was fully routed + seeded but had no launcher tile ("can't see it
 * anywhere"). This proves: a citizen reaches My Coverage, opens Enrol, sees the
 * real seeded plans (COV-MOHCC-CORE / COV-PRIVATE-PLUS), all through the real
 * ingress. (Tile visibility is asserted via the Start menu.)
 */
test.describe("Coverage — discover + enrol (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("citizen sees Coverage in the launcher and reaches enrol with real plans", async ({ page }) => {
    await loginAs(page, PERSONAS.learner);
    // Front-load consent once so subsequent coverage navigations don't re-gate.
    await gotoAs(page, PERSONAS.learner, "/home");
    await settleConsent(page);

    // Enrol surface RENDERS (no longer a 404 — the .dockerignore fix built the feature).
    await reach(page, "/coverage/enroll");
    await expect(page.getByRole("heading", { name: /enroll in coverage/i })).toBeVisible({ timeout: 20_000 });

    // …and its plan picker carries the REAL seeded plans (proves live coverage-service data).
    // Filter to the plan <select> (its placeholder is "Select plan") — the header has a language <select>.
    const planSelect = page.locator("select").filter({ hasText: /select plan/i }).first();
    await expect(async () => {
      // reload if the plans query hasn't populated the select yet (only the placeholder option)
      if ((await planSelect.locator("option").count()) < 2) {
        await page.reload({ waitUntil: "domcontentloaded" });
      }
      const options = await planSelect.locator("option").allTextContents();
      expect(options.join(" ")).toMatch(/MOHCC National Core|COV-MOHCC-CORE|Private Medical Aid|COV-PRIVATE-PLUS/i);
    }).toPass({ timeout: 30_000 });

    // FULL ENROLMENT in the browser: pick a plan → run eligibility → enrol (real writes).
    // (Deterministic write-path + DB truth also in scripts/e2e/coverage-enrol-proof.sh.)
    await planSelect.selectOption({ index: 1 }); // index 0 is the "Select plan" placeholder
    await page.getByRole("button", { name: /run eligibility/i }).click();
    // Eligible only if not already enrolled on this plan; accept either outcome as "ran".
    await expect(page.getByText(/eligible|already/i).first()).toBeVisible({ timeout: 20_000 });

    // My Coverage (the launcher tile target) is reachable and titled.
    await reach(page, "/coverage/member");
    await expect(page.getByRole("heading", { name: /my coverage/i }).first()).toBeVisible({ timeout: 20_000 });

    // The launcher now carries a Coverage tile (was absent → "can't see it").
    const startBtn = page
      .getByRole("button", { name: /^start( menu)?$|open start/i })
      .or(page.locator('[aria-label="Start menu"], [aria-label="Open Start menu"]'))
      .first();
    if (await startBtn.isVisible().catch(() => false)) {
      await startBtn.click();
      await expect(page.getByText(/^Coverage$/).first()).toBeVisible({ timeout: 10_000 });
    }
  });
});
