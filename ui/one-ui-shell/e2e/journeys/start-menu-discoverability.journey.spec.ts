import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, openStartMenu, acceptPoliciesIfGated } from "./honest-auth";

/**
 * Golden journey — Start Menu is the command centre.
 *
 * For each persona: real login, open the Start menu, and prove that the
 * services and quick actions their job needs are (a) present and (b) navigate
 * to a real page — not a 404 and not an error boundary. This is the executable
 * form of docs/demo/persona-truth-pack.md §"What each persona should see".
 */

test.describe("Start Menu discoverability (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  async function expectNoDeadEnd(page: import("@playwright/test").Page) {
    await expect(page.getByText(/404|page not found/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  }

  test("trainer sees authoring quick actions and Course Studio opens", async ({ page }) => {
    await loginAs(page, PERSONAS.trainer);
    await openStartMenu(page);
    await expect(page.getByRole("button", { name: /^new course$/i })).toBeVisible();
    await page.getByRole("button", { name: /^new course$/i }).click();
    await page.waitForURL(/\/learning\/studio/, { timeout: 20_000 });
    await acceptPoliciesIfGated(page);
    await expectNoDeadEnd(page);
  });

  test("clerk sees queue actions but no clinical hub", async ({ page }) => {
    await loginAs(page, PERSONAS.clerk);
    await openStartMenu(page);
    await expect(page.getByRole("button", { name: /register patient/i })).toBeVisible();
    // The gated launcher must not offer Clinical Hub; a DISABLED marketplace
    // card ("request access") is honest UX, so assert no launchable entry.
    await expect(page.getByRole("button", { name: /^clinical hub$/i, disabled: false })).toHaveCount(0);
    await page.getByRole("button", { name: /register patient/i }).click();
    await page.waitForURL(/\/queue\/walk-in/, { timeout: 20_000 });
    await expectNoDeadEnd(page);
  });

  test("doctor sees clinical services incl. telemedicine and diagnostics", async ({ page }) => {
    await loginAs(page, PERSONAS.doctor);
    await openStartMenu(page);
    await expect(page.getByText(/^Telemedicine$/).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /start teleconsult/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /diagnostic orders/i })).toBeVisible();
    await page.getByText(/^Telemedicine$/).first().click();
    await page.waitForURL(/\/telemedicine/, { timeout: 20_000 });
    await expectNoDeadEnd(page);
  });

  test("citizen sees wellness and life events, no admin actions", async ({ page }) => {
    await loginAs(page, PERSONAS.citizen);
    await openStartMenu(page);
    await expect(page.getByText(/simba wellness/i).first()).toBeVisible();
    await expect(page.getByText(/ubomi life events/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /issue provider id/i })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /workforce intake/i })).toHaveCount(0);
    await page.getByText(/simba wellness/i).first().click();
    await page.waitForURL(/\/wellness/, { timeout: 20_000 });
    await expectNoDeadEnd(page);
  });

  test("regulator (HIE_ADMIN) reaches the registry governance plane", async ({ page }) => {
    await loginAs(page, PERSONAS.regulator);
    await openStartMenu(page);
    // Deep actions render for the regulator without SYSTEM_ADMIN superpowers.
    await expect(page.getByRole("button", { name: /issue provider id/i })).toBeVisible();
    await page.getByRole("button", { name: /issue provider id/i }).click();
    await page.waitForURL(/\/registry\/providers\/new/, { timeout: 20_000 });
    await expectNoDeadEnd(page);
  });

  test("named operations services launch for the facility admin", async ({ page }) => {
    await loginAs(page, PERSONAS.facilityAdmin);
    await openStartMenu(page);
    for (const service of [/khuluma comms/i, /rito quality/i, /dura stores/i, /daidzai emergency/i]) {
      await expect(page.getByText(service).first()).toBeVisible();
    }
    await page.getByText(/khuluma comms/i).first().click();
    await page.waitForURL(/\/work\/comms/, { timeout: 20_000 });
    await expectNoDeadEnd(page);
  });
});
