import { test, expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — post-operative recovery → discharge → theatre utilisation reporting (Wave 4).
 *
 *   surgeon.moyo opens the Theatre Utilisation reporting surface (/reports/theatre), which runs the
 *   seeded `theatre-utilisation` report (reporting-service) and renders KPI tiles projected from real
 *   theatre.* events, then visits the theatre board to confirm case continuity.
 *
 * The PACU time-series, Aldrete discharge-readiness, NHUME PACU→ward/ICU transport, postop admission
 * linkage, surgical discharge summary (with surviving pending histopath), the COSTA surgical-case
 * bundle and the trainee CPD record are runtime-proven headless by
 * scripts/runtime-proof/theatre-recovery-reporting-journeys.sh — this spec proves the experience
 * surfaces reach the reporting projection. Skip-guarded so it no-ops when no estate is up.
 */

const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

test.describe.serial("Theatre recovery & utilisation reporting (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon opens the theatre utilisation reporting surface backed by real event metrics", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-recovery");

    await checklist.point("A1_access", "surgeon reaches the theatre utilisation surface", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/reports/theatre");
      await expect(page.getByRole("heading", { name: /theatre utilisation/i })).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_orientation", "the metric tiles orient the user to what the report covers", async () => {
      await expect(page.getByText(/reconcile with the cases actually run/i)).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText(/total cases/i)).toBeVisible({ timeout: 20_000 });
    });

    await checklist.notApplicable("A3_formOpens", "reporting surface has no data-entry form — it runs a report");

    await checklist.point("A5_action", "the Refresh control re-runs the theatre-utilisation report", async () => {
      const refresh = page.getByRole("button", { name: /refresh/i });
      await expectActionable(refresh);
    });

    await checklist.point("A6_persistedTruth", "refreshing hits the real reporting run endpoint", async () => {
      await expectSaved(page, "/internal/v1/reports/theatre-utilisation/run", async () => {
        await page.getByRole("button", { name: /refresh/i }).click();
      });
    });

    await checklist.point("A4_keyFields", "utilisation KPIs (cancellations, complications, usage) render", async () => {
      await expect(page.getByText(/cancellation rate/i)).toBeVisible();
      await expect(page.getByText(/complication cases/i)).toBeVisible();
      await expect(page.getByText(/blood units/i)).toBeVisible();
    });

    await checklist.point("A7_continuity", "the theatre board remains reachable for case continuity", async () => {
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await expect(page.getByText(/theatre list/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.notApplicable("A8_guidance", "no Nompilo guidance is surfaced on the reporting view");
    await checklist.notApplicable("A9_feedback", "no feedback capture on the reporting view");

    await checklist.point("A10_logicalEnd", "the journey ends on a coherent reporting + board surface", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre/);
    });

    await checklist.finalize(testInfo);
  });
});
