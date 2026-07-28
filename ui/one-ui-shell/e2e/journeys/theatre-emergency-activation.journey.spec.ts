import { test, expect } from "@playwright/test";
import { requireHealthId } from "./personas";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable } from "./acceptance";

/**
 * Golden journey (UI-completion Lane 1) — EMERGENCY rapid activation DRIVEN THROUGH THE SCREEN.
 *
 *   surgeon.moyo opens the theatre board, opens the on-screen "Emergency activation" surface, fills the
 *   rapid-activation form (patient + procedure + indication) and submits. The screen POSTs the real
 *   /internal/v1/theatre/cases/emergency endpoint (EMERGENCY triage forced, override armed, minimal
 *   preop) and routes to the created case page, where the emergency consent-exception surface is
 *   available. Skip-guarded so it no-ops when no estate is up; when preview is live it drives real UI.
 */
const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

test.describe.serial("Theatre emergency activation (live preview, screen-driven)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon activates an emergency case from the board and lands on the case page", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-emergency-activation");

    await checklist.point("A1_access", "surgeon reaches the theatre board", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await expect(page.getByText(/theatre list/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "the emergency rapid-activation surface opens on the screen", async () => {
      await page.getByTestId("emergency-activate-toggle").click();
      await expect(page.getByTestId("emergency-activate-form")).toBeVisible();
    });

    await checklist.point("A2_actionEnabled", "the surgeon captures patient + procedure and the submit is actionable", async () => {
      await page.getByTestId("em-patient").fill(requireHealthId(SURGEON));
      await page.getByTestId("em-procedure").fill("Emergency exploratory laparotomy");
      await expectActionable(page.getByTestId("em-submit"));
    });

    await checklist.point("A4_saves", "submitting drives the real emergency endpoint and routes to the new case", async () => {
      await Promise.all([
        page.waitForURL(/\/work\/clinical\/theatre\/[0-9a-f-]{8}/i, { timeout: 20_000 }),
        page.getByTestId("em-submit").click(),
      ]);
    });

    await checklist.point("A5_stateChange", "the case page shows the EMERGENCY case + override-gated consent surface", async () => {
      await expect(page.getByTestId("theatre-case-banner")).toBeVisible({ timeout: 20_000 });
      await expect(page.getByTestId("emergency-consent-panel")).toBeVisible();
    });

    await checklist.point("A10_logicalEnd", "the journey ends on the activated emergency case surface", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre\//);
    });

    await checklist.notApplicable("A6_crossUserVisibility", "single-actor activation flow");
    await checklist.notApplicable("A7_notification", "team mobilisation is emitted headless (outbox), not asserted here");
    await checklist.notApplicable("A8_audit", "the SAFETY audit event is proven by the runtime-proof rig");
    await checklist.notApplicable("A9_resume", "no resume step in this flow");
    await checklist.finalize(testInfo);
  });
});
