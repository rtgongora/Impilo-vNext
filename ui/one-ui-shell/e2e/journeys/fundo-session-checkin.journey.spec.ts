import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — Fundo scheduled session → learner attendance check-in.
 *
 * A facilitator's scheduled session and its check-in token are provisioned
 * out-of-band through the real ingress (there is no in-shell create-session /
 * token-issue authoring surface outside the live classroom — see the wave
 * report's Journey 4 notes), and the session id + title + code are injected via
 * env. This journey drives the LEARNER surface in a real browser: find the
 * session, open its detail, open check-in, enter the facilitator's code, and
 * record attendance via a real 2xx POST.
 *
 * Setup (run before this spec, capturing the printed values into env):
 *   FUNDO_SESSION_ID / FUNDO_SESSION_TITLE / FUNDO_CHECKIN_CODE
 * The live-classroom media join (ClassroomShell over Impilo Live) is a
 * documented GAP — not covered here.
 */

const SESSION_ID = process.env.FUNDO_SESSION_ID ?? "";
const SESSION_TITLE = process.env.FUNDO_SESSION_TITLE ?? "";
const CHECKIN_CODE = process.env.FUNDO_CHECKIN_CODE ?? "";

test.describe("Fundo session check-in journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
    test.skip(
      !SESSION_ID || !SESSION_TITLE || !CHECKIN_CODE,
      "Provision a session + token first and set FUNDO_SESSION_ID/TITLE/CHECKIN_CODE",
    );
  });

  test("learner: find session → check in → attendance recorded", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-session-checkin");

    await checklist.point("A1_access", "learner reaches the sessions list", async () => {
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, "/learning/sessions");
      await expect(page.getByText(/learning sessions/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A6_crossUserVisibility", "the facilitator's scheduled session is visible to the learner", async () => {
      await expect(page.getByRole("link", { name: SESSION_TITLE }).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "the session detail opens with a check-in entry", async () => {
      await gotoAs(page, PERSONAS.learner, `/learning/sessions/${SESSION_ID}`);
      await expect(page.getByText(SESSION_TITLE).first()).toBeVisible({ timeout: 20_000 });
      await expectActionable(page.getByTestId("session-checkin-link"));
    });

    await checklist.point("A2_actionEnabled", "check-in enables once a code is entered", async () => {
      await page.getByTestId("session-checkin-link").click();
      await page.waitForURL(/\/sessions\/.+\/checkin/, { timeout: 20_000 });
      const checkinButton = page.getByRole("button", { name: /check in/i });
      await expect(checkinButton).toBeDisabled();
      await page.getByPlaceholder(/6-digit code/i).fill(CHECKIN_CODE);
      await expectActionable(checkinButton);
    });

    await checklist.point("A4_saves", "check-in persists via a real POST", async () => {
      await expectSaved(page, `/internal/v1/learning/v11/sessions/${SESSION_ID}/checkin`, async () => {
        await page.getByRole("button", { name: /check in/i }).click();
      });
    });

    await checklist.point("A5_stateChange", "attendance now lists the learner as PRESENT", async () => {
      await expect(page.getByTestId("checkin-message")).toContainText(/checked in/i, { timeout: 15_000 });
      await expect(page.getByText(PERSONAS.learner.healthId ?? "c0000000-0000-4000-8000-000000000012").first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/present/i).first()).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A10_logicalEnd", "journey ends on a recorded, listed attendance", async () => {
      await expect(page.getByText(/attendance \(\d+\)/i).first()).toBeVisible();
    });

    await checklist.notApplicable("A7_notification", "self check-in does not notify; facilitator rosters surface attendance directly");
    await checklist.notApplicable("A8_audit", "attendance is recorded in lrn_session_attendance (recorded_by, check_in_at); no learner-facing audit surface");
    await checklist.notApplicable("A9_resume", "attendance is a terminal record for the session; resume is not applicable");
    await checklist.finalize(testInfo);
  });
});
