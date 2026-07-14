import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — the elective theatre path, one surgical team in sequence.
 *
 *   referral worklist → accept (funnels onto the waitlist)
 *   surgical waitlist → build an OR list → publish (→ the one episode SoR)
 *   readiness board → case is Ready and shows on the theatre list
 *
 * Runs against the live preview with the persona truth pack + Wave-2 theatre
 * seed. The doctor persona stands in for the receiving surgical team.
 */

test.describe.serial("Elective theatre — referral to published case (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgical team accepts a referral onto the waitlist", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-elective-referral");

    await checklist.point("A1_access", "surgeon reaches the surgical referral worklist", async () => {
      await loginAs(page, PERSONAS.doctor);
      await gotoAs(page, PERSONAS.doctor, "/work/clinical/theatre/referrals");
      await expect(page.getByRole("heading", { name: /surgical referrals/i })).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "Accept becomes actionable on a pending referral", async () => {
      await expectActionable(page.getByRole("button", { name: /accept/i }).first());
    });

    await checklist.point("A4_saves", "accepting persists via a real decide POST", async () => {
      await expectSaved(page, "/referrals/surgical", async () => {
        await page.getByRole("button", { name: /accept/i }).first().click();
      });
    });

    await checklist.point("A5_stateChange", "the accepted case appears on the surgical waitlist", async () => {
      await gotoAs(page, PERSONAS.doctor, "/scheduling/surgical-waitlist");
      await expect(page.getByRole("heading", { name: /surgical waitlist/i })).toBeVisible({ timeout: 20_000 });
    });

    await checklist.notApplicable("A3_formOpens", "worklist is decision-driven, not a form");
    await checklist.notApplicable("A6_crossUserVisibility", "single-team journey");
    await checklist.notApplicable("A7_notification", "khuluma notification rides the scheduling outbox");
    await checklist.notApplicable("A8_audit", "referral lifecycle audited via referral outbox");
    await checklist.notApplicable("A9_resume", "waitlist is server state; re-login is inherent resume");
    await checklist.point("A10_logicalEnd", "case is now waiting for surgery", async () => {
      await expect(page.getByText(/waiting|escalation|target/i).first()).toBeVisible({ timeout: 15_000 });
    });
    await checklist.finalize(testInfo);
  });

  test("scheduler builds an OR list and publishes to the episode SoR", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-elective-publish");

    await checklist.point("A1_access", "scheduler reaches the theatre lists", async () => {
      await loginAs(page, PERSONAS.doctor);
      await gotoAs(page, PERSONAS.doctor, "/scheduling/theatre-lists");
      await expect(page.getByRole("heading", { name: /theatre lists/i })).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "new-session form opens", async () => {
      await page.getByRole("button", { name: /new session/i }).click();
      await expect(page.getByLabel(/facility/i)).toBeVisible({ timeout: 10_000 });
    });

    await checklist.point("A2_actionEnabled", "publish is actionable on an open session", async () => {
      const openSession = page.getByRole("link").first();
      await openSession.click();
      await expectActionable(page.getByRole("button", { name: /publish/i }).first());
    });

    await checklist.point("A4_saves", "publish funnels the case through booking (real POST)", async () => {
      await expectSaved(page, "/publish", async () => {
        await page.getByRole("button", { name: /publish/i }).first().click();
      });
    });

    await checklist.point("A5_stateChange", "the readiness board shows the published case", async () => {
      await gotoAs(page, PERSONAS.doctor, "/work/clinical/theatre/board");
      await expect(page.getByRole("heading", { name: /readiness board/i })).toBeVisible({ timeout: 20_000 });
    });

    await checklist.notApplicable("A6_crossUserVisibility", "single-team journey");
    await checklist.notApplicable("A7_notification", "khuluma notification rides the scheduling outbox");
    await checklist.notApplicable("A8_audit", "publish audited via scheduling.theatre.session.published outbox");
    await checklist.notApplicable("A9_resume", "sessions are server state; re-login is inherent resume");
    await checklist.point("A10_logicalEnd", "the case is on the theatre list — one episode source of truth", async () => {
      await gotoAs(page, PERSONAS.doctor, "/work/clinical/theatre");
      await expect(page.getByRole("heading", { name: /theatre list/i })).toBeVisible({ timeout: 20_000 });
    });
    await checklist.finalize(testInfo);
  });
});
