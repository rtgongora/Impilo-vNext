import { test, expect } from "@playwright/test";
import { PREVIEW_ORIGIN, RUN_PREVIEW } from "./preview-sandbox-helpers";

/**
 * Scenario A — frontline health-worker journey, browser-level, HONEST AUTH.
 *
 * Unlike the preview-sandbox specs (synthetic session), this logs in through
 * the real /auth/login page against the live preview estate as a seeded
 * persona, so it exercises the full chain: Keycloak → health_id anchor →
 * linked-ids → workforce assignment → Work landing → Vashandi attendance.
 *
 * Gated like the other preview specs: PREVIEW_SANDBOX_E2E=1 or a preview
 * PLAYWRIGHT_BASE_URL. Requires scripts/operator/seed-scenario-a-estate.sh
 * to have passed against the target estate.
 */

const PERSONA = process.env.SCENARIO_A_PERSONA ?? "dr.mapfumo";
const PASSWORD = process.env.SCENARIO_A_PASSWORD ?? "ImpiloTest123!";

/** First login lands on the policy-consent gate — accept it like a real user. */
async function acceptPoliciesIfGated(page: import("@playwright/test").Page) {
  // The gate arrives via client-side redirect — give it a moment to settle.
  const gate = page.getByText(/review our policies/i).first();
  const gated = await gate.waitFor({ state: "visible", timeout: 5_000 }).then(() => true).catch(() => false);
  if (!gated) return;
  for (const box of await page.locator('input[type="checkbox"]').all()) {
    await box.check();
  }
  await page.getByRole("button", { name: /accept and continue/i }).click();
  await gate.waitFor({ state: "hidden", timeout: 15_000 });
}

test.describe("Scenario A — clinician login to shift check-in (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("real login lands with Work access", async ({ page }) => {
    await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
    await page.locator("#identifier").fill(PERSONA);
    await page.locator("#password").fill(PASSWORD);
    await page.locator('button[type="submit"]').click();

    // Post-login resolution should leave the auth pages entirely.
    await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
    await acceptPoliciesIfGated(page);

    // The seeded assignment chain must surface the Work tab (hasWorkAccess).
    await page.goto(`${PREVIEW_ORIGIN}/home`);
    await acceptPoliciesIfGated(page);
    await expect(page.getByRole("button", { name: /work/i }).first()).toBeVisible({ timeout: 20_000 });
  });

  test("attendance page binds the workforce profile and can check in", async ({ page }) => {
    await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
    await page.locator("#identifier").fill(PERSONA);
    await page.locator("#password").fill(PASSWORD);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
    await acceptPoliciesIfGated(page);

    await page.goto(`${PREVIEW_ORIGIN}/work/vashandi/attendance`);
    await acceptPoliciesIfGated(page);

    // The work zone is a windowed shell: satisfy the facility chooser if it
    // opened, then focus the Attendance window.
    const facilityOption = page.getByText(/harare central/i).first();
    if (await facilityOption.isVisible().catch(() => false)) {
      await facilityOption.click();
    }
    const attendanceChip = page.getByRole("button", { name: /^attendance$/i }).first();
    if (await attendanceChip.isVisible().catch(() => false)) {
      await attendanceChip.click();
    }

    // Profile bound → the check-in panel renders (session contract carries
    // vashandiWorkforceProfileId from the new session-context read-model).
    const checkInButton = page.getByRole("button", { name: /check in/i }).first();
    await expect(checkInButton).toBeVisible({ timeout: 20_000 });

    await checkInButton.click();
    await expect(page.getByText(/checked in/i).first()).toBeVisible({ timeout: 15_000 });
  });
});
