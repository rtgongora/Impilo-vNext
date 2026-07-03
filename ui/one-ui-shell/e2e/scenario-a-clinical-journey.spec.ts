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

    // The seeded assignment chain must surface the Work tab (hasWorkAccess).
    await page.goto(`${PREVIEW_ORIGIN}/home`);
    await expect(page.getByRole("button", { name: /work/i }).first()).toBeVisible({ timeout: 20_000 });
  });

  test("attendance page binds the workforce profile and can check in", async ({ page }) => {
    await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
    await page.locator("#identifier").fill(PERSONA);
    await page.locator("#password").fill(PASSWORD);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });

    await page.goto(`${PREVIEW_ORIGIN}/work/vashandi/attendance`);
    // Profile bound → the check-in panel renders (session contract carries
    // vashandiWorkforceProfileId from the new session-context read-model).
    const checkInButton = page.getByRole("button", { name: /check in/i });
    await expect(checkInButton).toBeVisible({ timeout: 20_000 });

    await checkInButton.click();
    await expect(page.getByText(/checked in/i).first()).toBeVisible({ timeout: 15_000 });
  });
});
