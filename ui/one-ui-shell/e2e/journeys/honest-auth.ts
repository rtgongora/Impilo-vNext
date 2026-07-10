/**
 * Honest-auth helpers for golden-journey specs.
 *
 * Every journey logs in through the REAL /auth/login page as a seeded persona —
 * no synthetic sessionStorage sessions. This exercises the full identity chain
 * (Keycloak → health_id anchor → linked-ids → workforce assignment) exactly as
 * a human user would.
 *
 * Extracted from scenario-a-clinical-journey.spec.ts so all journeys share one
 * login/gate/context idiom.
 */

import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { PERSONA_PASSWORD } from "./personas";

export { PREVIEW_ORIGIN, RUN_PREVIEW } from "../preview-sandbox-helpers";
import { PREVIEW_ORIGIN } from "../preview-sandbox-helpers";

/** First login lands on the policy-consent gate — accept it like a real user. */
export async function acceptPoliciesIfGated(page: Page) {
  const gate = page.getByText(/review our policies/i).first();
  const gated = await gate
    .waitFor({ state: "visible", timeout: 5_000 })
    .then(() => true)
    .catch(() => false);
  if (!gated) return;
  for (const box of await page.locator('input[type="checkbox"]').all()) {
    await box.check();
  }
  await page.getByRole("button", { name: /accept and continue/i }).click();
  await gate.waitFor({ state: "hidden", timeout: 15_000 });
}

/** Real login through /auth/login. Leaves the page outside /auth on success. */
export async function loginAs(page: Page, persona: JourneyPersona) {
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(persona.username);
  await page.locator("#password").fill(PERSONA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);
}

/** Sign out through the UI when possible; fall back to clearing the session. */
export async function logout(page: Page) {
  const signOut = page.getByRole("button", { name: /sign out|log ?out/i }).first();
  if (await signOut.isVisible().catch(() => false)) {
    // Toasts (Nompilo guidance) can intercept the pointer — fall back to a
    // session clear rather than failing the journey on a sign-out affordance.
    const clicked = await signOut
      .click({ timeout: 5_000 })
      .then(() => true)
      .catch(() => false);
    if (clicked) {
      await page.waitForURL((url) => url.pathname.startsWith("/auth"), { timeout: 15_000 }).catch(() => undefined);
      return;
    }
  }
  await page.evaluate(() => {
    sessionStorage.clear();
  });
  await page.context().clearCookies();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
}

/**
 * Complete the work-session start flow if the Workplace Selection Hub is up:
 * facility card (Enter) → workspace (Continue) → shift (Start Session).
 * Deep routes are guarded on this context — skipping it bounces you to Home.
 */
export async function ensureFacilityContext(page: Page, persona: JourneyPersona) {
  const pattern = persona.facilityNamePattern ?? /harare central/i;
  const onHub = await page
    .getByText(/start work session|workplace selection hub/i)
    .first()
    .isVisible({ timeout: 3_000 })
    .catch(() => false);
  if (!onHub) return;

  // 1. The facility card is a button whose accessible name carries the facility name.
  await page.getByRole("button", { name: pattern }).first().click();

  // 2. Workspace step (skipped by the shell for single-workspace facilities).
  const continueBtn = page.getByRole("button", { name: /continue/i }).first();
  if (await continueBtn.isVisible({ timeout: 10_000 }).catch(() => false)) {
    await continueBtn.click();
  }

  // 3. Shift step.
  const startSession = page.getByRole("button", { name: /start session/i }).first();
  if (await startSession.isVisible({ timeout: 10_000 }).catch(() => false)) {
    await startSession.click();
    await page
      .waitForURL((url) => !url.pathname.startsWith("/shift"), { timeout: 20_000 })
      .catch(() => undefined);
  }
}

/** Navigate within the shell, absorbing the consent gate and the work-session flow. */
export async function gotoAs(page: Page, persona: JourneyPersona, path: string) {
  await ensureFacilityContext(page, persona);
  await page.goto(`${PREVIEW_ORIGIN}${path}`);
  await acceptPoliciesIfGated(page);
}

/** Open the Start menu (dock launcher button) and assert it rendered. */
export async function openStartMenu(page: Page) {
  // The dock button's accessible name is its aria-label ("Start menu").
  const startButton = page
    .getByRole("button", { name: /^start( menu)?$|open start/i })
    .or(page.locator('[aria-label="Start menu"], [aria-label="Open Start menu"]'))
    .first();
  await startButton.click();
  await expect(page.getByText(/launch apps, utilities, and recent work/i)).toBeVisible({ timeout: 10_000 });
}
