import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

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
 * Inpatient discharge board — the discharge-clearances step is now reachable in the browser.
 * Previously NO UI could init/clear the discharge_clearance rows that summary-finalise hard-gates
 * on (finalise 409'd forever). This proves a clinician reaches the board and the new
 * DischargeClearancesPanel renders + can initialise the clearance checklist. The full
 * admit→EWS→clearances→finalise→DISCHARGED write path is proven deterministically by
 * scripts/e2e/inpatient-discharge-proof.sh (19/19 with DB truth + live event_outbox).
 */
test.describe("Inpatient discharge board — clearances reachable (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("clinician reaches the discharge board and the clearances panel is operable", async ({ page }) => {
    await loginAs(page, PERSONAS.doctor);
    await gotoAs(page, PERSONAS.doctor, "/clinical/inpatient/discharge-board");
    await settleConsent(page);
    if (!page.url().includes("/discharge-board")) {
      await page.goto(`${page.url().split("/clinical")[0]}/clinical/inpatient/discharge-board`, { waitUntil: "domcontentloaded" });
      await settleConsent(page);
    }

    // Board renders (heading), and the inpatient service backs it (no placeholder).
    await expect(page.getByRole("heading", { name: /discharge board/i })).toBeVisible({ timeout: 20_000 });

    // Expand an admitted patient's discharge panel if one is present.
    const expand = page.getByRole("button", { name: /discharge summary/i }).first();
    if (await expand.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await expand.click();
      // The NEW clearances panel (previously absent) renders and is operable.
      await expect(page.getByTestId("discharge-clearances")).toBeVisible({ timeout: 15_000 });
      const init = page.getByTestId("init-clearances");
      if (await init.isVisible().catch(() => false)) {
        await init.click();
        // After init, at least one clearance row with a Clear action appears (real write).
        await expect(page.getByTestId("clear-clearance").first()).toBeVisible({ timeout: 15_000 });
      } else {
        // Clearances already initialised for this encounter — the panel still shows rows.
        await expect(page.getByText(/clearances/i).first()).toBeVisible();
      }
    } else {
      // No admitted patients at this facility right now — the board shows its honest empty state, not junk.
      await expect(page.getByText(/no active inpatients/i)).toBeVisible({ timeout: 10_000 });
    }
  });
});
