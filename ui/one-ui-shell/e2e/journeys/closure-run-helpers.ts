/**
 * Shared helpers for the C9 closure-run citizen acceptance suite.
 *
 * These 30 journeys are MOSTLY PUBLIC/ANONYMOUS — they exercise the citizen
 * front door of the live preview estate exactly as an ordinary person would,
 * with no seeded session. Every journey attaches the 10-point AcceptanceChecklist
 * and records honest N/A (with a reason) for points that do not apply to a
 * read-only public surface — silent omission is the "false green" this wave kills.
 */

import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { PREVIEW_ORIGIN } from "../preview-sandbox-helpers";
import { AcceptanceChecklist, type AcceptancePoint } from "./acceptance";

export { PREVIEW_ORIGIN };
export { AcceptanceChecklist };

/** Navigate to a public path and wait for the app shell to paint. */
export async function gotoPublic(page: Page, path: string) {
  await page.goto(`${PREVIEW_ORIGIN}${path}`, { waitUntil: "domcontentloaded" });
  // Public pages render inside <main id="main-content"> (PublicShell) or an auth layout.
  await page
    .locator("#main-content, main, form")
    .first()
    .waitFor({ state: "visible", timeout: 20_000 })
    .catch(() => undefined);
}

/**
 * The live estate carries a public MODAL service advisory ("Help us build the
 * future…") on non-never-block routes (e.g. /welcome). It is deliberately
 * dismissible and non-blocking; dismiss it so it does not intercept clicks in
 * chrome/keyboard journeys. No-op when absent or already dismissed.
 */
export async function dismissAdvisoryIfPresent(page: Page) {
  const modal = page.getByTestId("advisory-modal");
  if (await modal.isVisible({ timeout: 2_000 }).catch(() => false)) {
    const continueBtn = page.getByRole("button", { name: /not now — continue/i }).first();
    if (await continueBtn.isVisible().catch(() => false)) {
      await continueBtn.click().catch(() => undefined);
    } else {
      await page.getByRole("button", { name: /dismiss this notice/i }).first().click().catch(() => undefined);
    }
    await modal.waitFor({ state: "hidden", timeout: 5_000 }).catch(() => undefined);
    return;
  }
  // Banner / inline / bubble variants carry a Dismiss control too.
  const anyDismiss = page.getByRole("button", { name: /^dismiss$/i }).first();
  if (await anyDismiss.isVisible({ timeout: 500 }).catch(() => false)) {
    await anyDismiss.click().catch(() => undefined);
  }
}

/** Wait for a successful (2xx) GET against a public-gateway path fragment. */
export async function waitForGatewayGet(page: Page, fragment: string, timeoutMs = 30_000) {
  return page
    .waitForResponse(
      (r) =>
        r.request().method() === "GET" &&
        r.url().includes(fragment) &&
        r.status() >= 200 &&
        r.status() < 300,
      { timeout: timeoutMs },
    )
    .catch(() => null);
}

/** Wait for a successful (2xx) POST against a public-gateway path fragment. */
export async function waitForGatewayPost(page: Page, fragment: string, timeoutMs = 30_000) {
  return page
    .waitForResponse(
      (r) =>
        r.request().method() === "POST" &&
        r.url().includes(fragment) &&
        r.status() >= 200 &&
        r.status() < 300,
      { timeout: timeoutMs },
    )
    .catch(() => null);
}

/**
 * Run a find-care search that reliably yields real facility cards on the live
 * estate: select province "Harare" (area-browse lane returns the seeded Harare
 * facilities). Returns the number of result cards rendered.
 */
export async function browseHarareFacilities(page: Page): Promise<number> {
  const search = waitForGatewayGet(page, "/public/gateway/find-care/search");
  await page.locator("#find-care-province").selectOption("Harare");
  await search;
  await page.getByLabel("Search results").waitFor({ state: "visible", timeout: 20_000 }).catch(() => undefined);
  return page.locator('article[aria-label]').count();
}

/** Bulk-record acceptance points as N/A with a shared reason. */
export async function naPoints(
  chk: AcceptanceChecklist,
  points: AcceptancePoint[],
  reason: string,
) {
  for (const p of points) {
    await chk.notApplicable(p, reason);
  }
}

/** A unique marker string for draft/idempotency assertions. */
export function marker(prefix: string) {
  return `${prefix}-${Date.now().toString(36)}`;
}

/** Assert no fabricated "open now" live-status claim appears in a results region. */
export async function assertNoFabricatedOpenNow(page: Page) {
  // Register status is not a live signal; the UI must never render a bare "Open now".
  await expect(page.getByText(/\bopen now\b/i)).toHaveCount(0);
}
