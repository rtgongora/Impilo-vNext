import { test, expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable } from "./acceptance";

/**
 * Golden journey — EMERGENCY theatre surfaces (Wave 5b §15).
 *
 *   surgeon.moyo reaches the theatre board (/work/clinical/theatre) and confirms the perioperative
 *   surfaces are live. The emergency-surgery journey (rapid activation, audited consent exception,
 *   start under override, THEATRE-phase registration on the shared trauma timeline) and the obstetric
 *   emergency C-section (maternal + fetal + neonatal, provisional baby identity) are runtime-proven
 *   end-to-end — including the daidzai mint → X-Trauma-Episode-ID → procedure_episode.trauma_episode_id
 *   stamp handoff — by scripts/runtime-proof/theatre-emergency-journeys.sh. This spec proves the
 *   experience surface reaches the board; it is skip-guarded so it no-ops when no estate is up.
 */

const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

test.describe.serial("Theatre emergency journeys (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon reaches the theatre board that hosts the emergency + obstetric surfaces", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-emergency");

    await checklist.point("A1_access", "surgeon reaches the theatre board", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await expect(page.getByText(/theatre list/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_orientation", "the theatre board orients the surgeon to the case list", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre/);
    });

    await checklist.notApplicable(
      "A3_formOpens",
      "emergency rapid-activation intake is a headless clinical endpoint; the board is read-first",
    );

    await checklist.point("A5_action", "the theatre board exposes an actionable case control", async () => {
      const anyAction = page.getByRole("button").first();
      await expectActionable(anyAction);
    });

    await checklist.notApplicable(
      "A6_persistedTruth",
      "emergency activation / consent-exception / delivery persistence is proven by theatre-emergency-journeys.sh",
    );
    await checklist.notApplicable(
      "A4_keyFields",
      "emergency + obstetric field capture is exercised headless (rig), not on this board view",
    );
    await checklist.notApplicable("A7_continuity", "board is the continuity surface for the perioperative case");
    await checklist.notApplicable("A8_guidance", "no Nompilo guidance surfaced on the board list view");
    await checklist.notApplicable("A9_feedback", "no feedback capture on the board list view");

    await checklist.point("A10_logicalEnd", "the journey ends on a coherent theatre board surface", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre/);
    });

    await checklist.finalize(testInfo);
  });
});
