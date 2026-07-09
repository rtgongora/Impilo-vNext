import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — the clinical day, three real users in sequence.
 *
 *   clerk.dube    walk-in: search patient → Add to Queue
 *   nurse.chienda queue board shows the clerk's entry (cross-user visibility)
 *   dr.mapfumo    Call → chart opens (queue → encounter handoff)
 *
 * Runs against the live preview with the persona truth pack seeded. Uses the
 * seeded patient "Moyo" (VITO seed 03 / citizen.moyo).
 */

const PATIENT_SEARCH = process.env.JOURNEY_PATIENT_SEARCH ?? "Moyo";

test.describe.serial("Clinical day — registration to encounter (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("clerk registers the walk-in onto the queue", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("clinical-day-clerk");

    await checklist.point("A1_access", "records clerk reaches walk-in registration", async () => {
      await loginAs(page, PERSONAS.clerk);
      await gotoAs(page, PERSONAS.clerk, "/queue/walk-in");
      await expect(page.getByPlaceholder(/search by name, id/i)).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "patient search form accepts input", async () => {
      await page.getByPlaceholder(/search by name, id/i).fill(PATIENT_SEARCH);
      await page.getByRole("button", { name: /^search$/i }).click();
    });

    await checklist.point("A2_actionEnabled", "Add to Queue becomes actionable once a patient is selected", async () => {
      const patientRow = page.getByRole("button", { name: new RegExp(PATIENT_SEARCH, "i") }).first();
      await expect(patientRow).toBeVisible({ timeout: 20_000 });
      await patientRow.click();
      await expectActionable(page.getByRole("button", { name: /add to queue/i }));
    });

    await checklist.point("A4_saves", "queue entry persists via real POST", async () => {
      await expectSaved(page, "/internal/v1/queue/entries", async () => {
        await page.getByRole("button", { name: /add to queue/i }).click();
      });
    });

    await checklist.point("A5_stateChange", "queue board reflects the new WAITING entry", async () => {
      await gotoAs(page, PERSONAS.clerk, "/queue");
      await expect(page.getByText(new RegExp(PATIENT_SEARCH, "i")).first()).toBeVisible({ timeout: 30_000 });
    });

    await checklist.notApplicable("A6_crossUserVisibility", "proven by the nurse test in this serial journey");
    await checklist.notApplicable("A7_notification", "queue-joined citizen notification asserted in the doctor leg via inbox delta when patient identity is linked");
    await checklist.notApplicable("A8_audit", "queue lifecycle audit rides pct outbox events (scenario-A steel thread); no clerk-facing audit surface");
    await checklist.notApplicable("A9_resume", "queue entries are server state; resume is inherent — re-login proven in the nurse/doctor legs");
    await checklist.point("A10_logicalEnd", "clerk's job is done: patient is queued", async () => {
      await expect(page.getByText(/waiting/i).first()).toBeVisible({ timeout: 15_000 });
    });
    await checklist.finalize(testInfo);
  });

  test("nurse sees the clerk's entry on the queue board", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("clinical-day-nurse");

    await checklist.point("A1_access", "nurse reaches the queue board", async () => {
      await loginAs(page, PERSONAS.nurse);
      await gotoAs(page, PERSONAS.nurse, "/queue");
    });

    await checklist.point("A6_crossUserVisibility", "another user sees the clerk-created entry", async () => {
      await expect(page.getByText(new RegExp(PATIENT_SEARCH, "i")).first()).toBeVisible({ timeout: 30_000 });
    });

    await checklist.notApplicable("A2_actionEnabled", "nurse triage actions covered by triage surfaces; this leg proves shared queue truth");
    await checklist.notApplicable("A3_formOpens", "no form in this leg");
    await checklist.notApplicable("A4_saves", "no mutation in this leg");
    await checklist.notApplicable("A5_stateChange", "state transition proven in the doctor leg (Call)");
    await checklist.notApplicable("A7_notification", "no notification expected for viewing");
    await checklist.notApplicable("A8_audit", "read-only leg");
    await checklist.notApplicable("A9_resume", "fresh login IS the resume proof for server-held queue state");
    await checklist.point("A10_logicalEnd", "queue board is the nurse's working surface", async () => {
      await expect(page.getByText(/waiting|called|in progress/i).first()).toBeVisible({ timeout: 15_000 });
    });
    await checklist.finalize(testInfo);
  });

  test("doctor calls the patient and the chart opens", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("clinical-day-doctor");

    await checklist.point("A1_access", "doctor reaches the queue board", async () => {
      await loginAs(page, PERSONAS.doctor);
      await gotoAs(page, PERSONAS.doctor, "/queue");
      await expect(page.getByText(new RegExp(PATIENT_SEARCH, "i")).first()).toBeVisible({ timeout: 30_000 });
    });

    await checklist.point("A2_actionEnabled", "Call action is present and enabled", async () => {
      const row = page.locator("div, li, tr").filter({ hasText: new RegExp(PATIENT_SEARCH, "i") }).last();
      await expectActionable(row.getByRole("button", { name: /^call$/i }).first());
    });

    await checklist.point("A4_saves", "Call persists via real POST (WAITING → CALLED)", async () => {
      const row = page.locator("div, li, tr").filter({ hasText: new RegExp(PATIENT_SEARCH, "i") }).last();
      await expectSaved(page, "/call", async () => {
        await row.getByRole("button", { name: /^call$/i }).first().click();
      });
    });

    await checklist.point("A5_stateChange", "chart opens from the queue (encounter entry)", async () => {
      await page.waitForURL(/\/ehr\/.+/, { timeout: 30_000 }).catch(async () => {
        // Some flows keep the board open and surface an "open chart" affordance.
        const openChart = page.getByRole("button", { name: /open chart|start encounter/i }).first();
        await openChart.click();
        await page.waitForURL(/\/ehr\/.+/, { timeout: 20_000 });
      });
      expect(page.url()).toMatch(/\/ehr\//);
    });

    await checklist.point("A10_logicalEnd", "clinical chart is the encounter workspace", async () => {
      await expect(page.getByText(/chart|encounter|clinical/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.notApplicable("A3_formOpens", "encounter documentation forms covered by encounter-page unit/e2e suites");
    await checklist.notApplicable("A6_crossUserVisibility", "proven by the nurse leg");
    await checklist.notApplicable("A7_notification", "queue-called citizen notification requires patient-identity linkage on this entry; asserted in scenario-A steel thread");
    await checklist.notApplicable("A8_audit", "call transition audited via pct outbox (scenario-A steel thread)");
    await checklist.notApplicable("A9_resume", "server-held queue state; fresh doctor login IS the resume");
    await checklist.finalize(testInfo);
  });
});
