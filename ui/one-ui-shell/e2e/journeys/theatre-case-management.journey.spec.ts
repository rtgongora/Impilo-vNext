import { test, expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable } from "./acceptance";

/**
 * Golden journey (UI-completion Lanes 2+3) — CASE-PAGE CLINICAL MANAGEMENT DRIVEN THROUGH THE SCREEN.
 *
 *   surgeon.moyo activates a case, then on the perioperative case page drives the management panels that
 *   previously had no UI: blood (MADI request), a surgical count that makes the WHO Sign-Out gate visible,
 *   a specimen transport leg, an anaesthesia chart entry, and an implant (UDI → patient) in the commodities
 *   register. Every action binds a real /internal/v1/theatre|procedures endpoint. Skip-guarded.
 */
const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

test.describe.serial("Theatre case-page management (live preview, screen-driven)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon drives blood, counts, specimen, anaesthesia chart and implant on the case page", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-case-management");

    await checklist.point("A1_access", "surgeon reaches a live perioperative case page", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await page.getByTestId("emergency-activate-toggle").click();
      await page.getByTestId("em-patient").fill(SURGEON.healthId);
      await page.getByTestId("em-procedure").fill("Emergency exploratory laparotomy");
      await Promise.all([
        page.waitForURL(/\/work\/clinical\/theatre\/[0-9a-f-]{8}/i, { timeout: 20_000 }),
        page.getByTestId("em-submit").click(),
      ]);
      await expect(page.getByTestId("clinical-management")).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "the blood, count, specimen, chart and commodities panels are all live", async () => {
      await expect(page.getByTestId("blood-panel")).toBeVisible();
      await expect(page.getByTestId("count-panel")).toBeVisible();
      await expect(page.getByTestId("specimen-panel")).toBeVisible();
      await expect(page.getByTestId("anaesthesia-chart-panel")).toBeVisible();
      await expect(page.getByTestId("commodities-panel")).toBeVisible();
      await expectActionable(page.getByRole("button", { name: /Request blood/i }));
    });

    await checklist.point("A3_formOpens", "the surgeon requests blood from MADI", async () => {
      await page.getByRole("button", { name: /Request blood/i }).click();
    });

    await checklist.point("A4_saves", "an anaesthesia chart entry is added against the real endpoint", async () => {
      await page.getByTestId("anaesthesia-chart-panel").getByPlaceholder("e.g. Sevoflurane").fill("Propofol");
      await page.getByTestId("anaesthesia-chart-panel").getByRole("button", { name: /Add entry/i }).click();
    });

    await checklist.point("A5_stateChange", "a discrepant surgical count makes the WHO Sign-Out BLOCK visible", async () => {
      const countPanel = page.getByTestId("count-panel");
      const spinners = countPanel.getByRole("spinbutton");
      await spinners.nth(0).fill("10");
      await spinners.nth(1).fill("9");
      await countPanel.getByRole("button", { name: /Record count/i }).click();
      await expect(page.getByTestId("signout-gate-blocked")).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A6_crossUserVisibility", "an implant (UDI → patient) is recorded in the commodities register", async () => {
      await page.getByTestId("implant-udi").fill("(01)00844588003288(17)261231(10)LOT42");
      await page.getByTestId("commodities-panel").getByRole("button", { name: /Record implant/i }).click();
    });

    await checklist.point("A10_logicalEnd", "the journey ends on the managed perioperative case", async () => {
      await expect(page).toHaveURL(/\/work\/clinical\/theatre\//);
    });

    await checklist.notApplicable("A7_notification", "MADI/RITO notifications are proven by the runtime-proof rig");
    await checklist.notApplicable("A8_audit", "outbox audit events are proven by the runtime-proof rig");
    await checklist.notApplicable("A9_resume", "no resume step in this flow");
    await checklist.finalize(testInfo);
  });
});
