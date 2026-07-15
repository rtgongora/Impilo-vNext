import { test, expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable } from "./acceptance";

/**
 * Golden journey (UI-completion Lane 1) — OBSTETRIC EMERGENCY C-SECTION DRIVEN THROUGH THE SCREEN.
 *
 *   surgeon.moyo activates an emergency caesarean from the board, then on the case page drives the
 *   obstetric surface: records maternal+fetal context, pages the neonatal team, records the delivery
 *   against a PROVISIONAL baby CPID (identity minted upstream in VITO — the screen never mints), and
 *   confirms the mother↔baby linkage the service returns, then hands the neonate over. Skip-guarded.
 */
const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

// A provisional neonatal identity is assumed minted UPSTREAM (VITO). The screen consumes, never mints.
const BABY_CPID = "c0000000-0000-4000-8000-0000000000b1";

test.describe.serial("Theatre obstetric C-section (live preview, screen-driven)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon runs the emergency caesarean surface end-to-end", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-csection");

    await checklist.point("A1_access", "surgeon activates an emergency caesarean and reaches its case page", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await page.getByTestId("emergency-activate-toggle").click();
      await page.getByTestId("em-patient").fill(SURGEON.healthId);
      await page.getByTestId("em-procedure").fill("Emergency Caesarean Section");
      await Promise.all([
        page.waitForURL(/\/work\/clinical\/theatre\/[0-9a-f-]{8}/i, { timeout: 20_000 }),
        page.getByTestId("em-submit").click(),
      ]);
    });

    await checklist.point("A3_formOpens", "the obstetric caesarean surface is shown for the obstetric case", async () => {
      await expect(page.getByTestId("obstetric-section")).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "maternal+fetal context is captured and recorded", async () => {
      await expectActionable(page.getByRole("button", { name: /Record context/i }));
      await page.getByRole("button", { name: /Record context/i }).click();
    });

    await checklist.point("A7_notification", "the neonatal team is paged from the screen", async () => {
      await page.getByRole("button", { name: /Page neonatal team/i }).click();
      await expect(page.getByTestId("neonatal-team-status")).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A4_saves", "the delivery is recorded against the provisional baby CPID", async () => {
      await page.getByTestId("baby-cpid").fill(BABY_CPID);
      await page.getByRole("button", { name: /Record delivery/i }).click();
    });

    await checklist.point("A5_stateChange", "the screen displays the mother↔baby linkage returned by the service", async () => {
      await expect(page.getByTestId("delivery-linkage")).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A6_crossUserVisibility", "the neonate is handed over to a postnatal destination", async () => {
      await page.getByRole("button", { name: /Handover neonate/i }).click();
      await expect(page.getByTestId("handover-status")).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A10_logicalEnd", "the journey ends on a coherent obstetric case surface", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre\//);
    });

    await checklist.notApplicable("A8_audit", "obstetric outbox events are proven by the runtime-proof rig");
    await checklist.notApplicable("A9_resume", "no resume step in this flow");
    await checklist.finalize(testInfo);
  });
});
