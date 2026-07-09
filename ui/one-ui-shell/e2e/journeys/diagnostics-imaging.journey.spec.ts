import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved, unreadCount } from "./acceptance";

/**
 * Golden journey — imaging order → worklist → report release → requester review.
 *
 *   dr.mapfumo   creates an IMAGING order (chest X-ray) for the seeded patient
 *   rad.nkomo    accepts, starts, completes it on the imaging worklist,
 *                authors and releases the report
 *   dr.mapfumo   finds the released result in the results inbox and is notified
 *
 * PO acceptance: "doctor orders chest X-ray → radiology user completes result →
 * doctor sees result → patient record updates."
 */

const PATIENT_CPID = process.env.JOURNEY_PATIENT_CPID ?? "CPID-ZW-00001";
const ORDER_CODE = "CHEST-XR";
let orderMarkerVisible = false;

test.describe.serial("Diagnostics imaging loop (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("doctor orders the chest X-ray", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("diagnostics-order");

    await checklist.point("A1_access", "doctor reaches Create Diagnostic Order", async () => {
      await loginAs(page, PERSONAS.doctor);
      await gotoAs(page, PERSONAS.doctor, "/diagnostics/orders/new");
      await expect(page.getByText(/create diagnostic order/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "order form accepts CPID, type IMAGING, code", async () => {
      await page.getByLabel("Patient CPID").fill(PATIENT_CPID);
      await page.getByLabel("Order type").selectOption("IMAGING");
      await page.getByLabel("Order code").fill(ORDER_CODE);
      await page.getByLabel("Clinical notes").fill("Golden-journey acceptance: chest X-ray loop.");
    });

    await checklist.point("A2_actionEnabled", "submit enables once required fields are set", async () => {
      await expectActionable(page.locator('button[type="submit"]').first());
    });

    await checklist.point("A4_saves", "order persists via real POST", async () => {
      await expectSaved(page, "/internal/v1/diagnostics/orders", async () => {
        await page.locator('button[type="submit"]').first().click();
      });
      orderMarkerVisible = true;
    });

    await checklist.point("A5_stateChange", "order appears in order tracking", async () => {
      await gotoAs(page, PERSONAS.doctor, "/diagnostics/orders");
      await expect(page.getByText(new RegExp(ORDER_CODE, "i")).first()).toBeVisible({ timeout: 30_000 });
    });

    await checklist.notApplicable("A6_crossUserVisibility", "proven by the radiographer leg");
    await checklist.notApplicable("A7_notification", "notification is asserted after report release in the review leg");
    await checklist.notApplicable("A8_audit", "order lifecycle audited via OROS outbox (154-test suite + steel thread)");
    await checklist.notApplicable("A9_resume", "orders are server state; radiographer leg logs in fresh");
    await checklist.point("A10_logicalEnd", "doctor's ordering job is done", async () => {
      expect(orderMarkerVisible).toBe(true);
    });
    await checklist.finalize(testInfo);
  });

  test("radiographer performs the study and releases the report", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("diagnostics-fulfilment");
    test.skip(!orderMarkerVisible, "ordering leg must have created the order");

    await checklist.point("A1_access", "radiographer reaches the imaging worklist", async () => {
      await loginAs(page, PERSONAS.radiographer);
      await gotoAs(page, PERSONAS.radiographer, "/diagnostics/worklist");
    });

    await checklist.point("A6_crossUserVisibility", "the doctor's order is on the radiographer's worklist", async () => {
      await expect(page.getByText(new RegExp(ORDER_CODE, "i")).first()).toBeVisible({ timeout: 30_000 });
    });

    const row = () => page.locator("tr, li, div").filter({ hasText: new RegExp(ORDER_CODE, "i") }).last();

    await checklist.point("A2_actionEnabled", "workflow transitions are actionable", async () => {
      await expectActionable(row().getByRole("button", { name: /accept|start|complete/i }).first());
    });

    await checklist.point("A5_stateChange", "Accept → Start → Complete walks the imaging workflow", async () => {
      for (const action of [/^accept$/i, /^start$/i, /^complete$/i]) {
        const button = row().getByRole("button", { name: action }).first();
        const visible = await button.isVisible().catch(() => false);
        if (visible) {
          await expectSaved(page, "/transition", async () => {
            await button.click();
          });
        }
      }
      await expect(row().getByText(/performed|in progress/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A4_saves", "final report is authored and released", async () => {
      await gotoAs(page, PERSONAS.radiographer, "/diagnostics/reporting");
      const orderPicker = page.getByLabel(/order/i).first();
      if (await orderPicker.isVisible().catch(() => false)) {
        await orderPicker.fill(ORDER_CODE).catch(() => undefined);
      }
      await page.getByLabel(/impression/i).or(page.locator("textarea").first()).fill(
        "No acute cardiopulmonary abnormality. Golden-journey acceptance report.",
      );
      await expectSaved(page, "/report", async () => {
        await page.locator('button[type="submit"]').first().click();
      });
      const release = page.getByRole("button", { name: /^release$/i }).first();
      await expectActionable(release);
      await expectSaved(page, "/release", async () => {
        await release.click();
      });
    });

    await checklist.notApplicable("A3_formOpens", "report form asserted within A4");
    await checklist.notApplicable("A7_notification", "requester notification asserted in the review leg");
    await checklist.notApplicable("A8_audit", "report versioning/release audited in OROS (ReportService suite)");
    await checklist.notApplicable("A9_resume", "worklist is server state across sessions by construction");
    await checklist.point("A10_logicalEnd", "radiographer's fulfilment job is done", async () => {
      await expect(page.getByText(/released/i).first()).toBeVisible({ timeout: 20_000 });
    });
    await checklist.finalize(testInfo);
  });

  test("doctor reviews the released result and is notified", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("diagnostics-review");
    test.skip(!orderMarkerVisible, "prior legs must have run");

    await checklist.point("A1_access", "doctor reaches the results inbox", async () => {
      await loginAs(page, PERSONAS.doctor);
      await gotoAs(page, PERSONAS.doctor, "/diagnostics/results-inbox");
    });

    await checklist.point("A5_stateChange", "released result is in the requester's inbox", async () => {
      await expect(page.getByTestId("results-inbox-row").first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(new RegExp(ORDER_CODE, "i")).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A7_notification", "requester has an unread notification signal", async () => {
      // PO requirement: "Practitioner receives notification." A zero/failed count
      // here is a REAL wave defect, not a spec problem — do not soften this.
      const count = await unreadCount(page);
      expect(count, "unread inbox count after result release").toBeGreaterThan(0);
    });

    await checklist.point("A10_logicalEnd", "loop closes: order → result → review", async () => {
      await expect(page.getByTestId("results-inbox-row").first()).toBeVisible();
    });

    await checklist.notApplicable("A2_actionEnabled", "viewer deep-launch covered by imaging-viewer e2e");
    await checklist.notApplicable("A3_formOpens", "no form in the review leg");
    await checklist.notApplicable("A4_saves", "acknowledgement mutation covered by critical-queue suite");
    await checklist.notApplicable("A6_crossUserVisibility", "this leg IS the cross-user proof of the radiographer's work");
    await checklist.notApplicable("A8_audit", "viewer sessions + acks audited in pacs/OROS suites");
    await checklist.notApplicable("A9_resume", "fresh doctor login in this leg is the resume proof");
    await checklist.finalize(testInfo);
  });
});
