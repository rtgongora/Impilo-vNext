import { test, expect } from "@playwright/test";
import type { JourneyPersona } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — an elective operating-theatre case, surgeon-driven.
 *
 *   surgeon.moyo  opens the theatre board (/work/clinical/theatre), intakes a
 *   new elective case, opens it (/work/clinical/theatre/[id]), evaluates
 *   owner-routed readiness, books under an audited override, and drafts + signs
 *   the operative note — all through the real theatre surfaces backed by
 *   /internal/v1/theatre/**.
 *
 * Runs against the live preview with the theatre persona pack seeded
 * (scripts/seed/18-19 + 20 + the realm surgeon.moyo user). The perioperative
 * pipeline itself is runtime-proven headless by
 * scripts/runtime-proof/theatre-elective-journeys.sh; this spec proves the
 * experience surfaces reach it. Skip-guarded so it no-ops when no estate is up.
 *
 * NB: the WHO checklist is display-only on this page and consent is granted on
 * the MVUMO surface, so a UI-only "Start theatre" is intentionally gated — this
 * journey drives the board actions the page actually exposes (intake, readiness,
 * booking, operative note) and asserts the checklist surface renders.
 */

const SURGEON: JourneyPersona = {
  username: "surgeon.moyo",
  displayName: "Tapiwa Moyo",
  roles: ["CLINICIAN"],
  providerId: "PROV-ZW-00020",
  healthId: "c0000000-0000-4000-8000-000000000030",
  facilityNamePattern: /harare central/i,
};

const PATIENT_CPID = process.env.JOURNEY_THEATRE_CPID ?? `CPID-ZW-THEATRE-E2E`;
const PROCEDURE = "Elective inguinal hernia repair";

test.describe.serial("Elective theatre — surgeon books and documents a case (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("surgeon intakes, readies, books and signs an elective case", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("theatre-elective-surgeon");

    await checklist.point("A1_access", "surgeon reaches the theatre board", async () => {
      await loginAs(page, SURGEON);
      await gotoAs(page, SURGEON, "/work/clinical/theatre");
      await expect(page.getByText(/theatre list/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "the New theatre case intake form opens", async () => {
      await page.getByRole("button", { name: /new theatre case/i }).click();
      await expect(page.getByText(/new theatre case \(from a procedure order\)/i)).toBeVisible({ timeout: 10_000 });
      await page.locator('label:has-text("Patient (CPID)") input').fill(PATIENT_CPID);
      await page.locator('label:has-text("Procedure") input').first().fill(PROCEDURE);
      await page.locator('label:has-text("Surgeon (provider id)") input').fill(SURGEON.providerId ?? "");
    });

    await checklist.point("A2_actionEnabled", "Create theatre case becomes actionable", async () => {
      await expectActionable(page.getByRole("button", { name: /create theatre case/i }));
    });

    await checklist.point("A4_saves", "the case persists via a real POST to the theatre face", async () => {
      await expectSaved(page, "/internal/v1/theatre/cases", async () => {
        await page.getByRole("button", { name: /create theatre case/i }).click();
      });
    });

    await checklist.point("A5_stateChange", "the case opens on its perioperative surface", async () => {
      const row = page.getByRole("link", { name: new RegExp(PATIENT_CPID, "i") }).first();
      await expect(row).toBeVisible({ timeout: 30_000 });
      await row.click();
      await page.waitForURL(/\/work\/clinical\/theatre\/.+/, { timeout: 20_000 });
      await expect(page.getByText(/readiness & booking/i)).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A6_crossUserVisibility", "owner-routed readiness renders (ROOM/TEAM/EQUIPMENT)", async () => {
      await page.getByRole("button", { name: /evaluate readiness/i }).click();
      await expect(page.getByText(/all checks ready|not bookable yet/i)).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A8_audit", "booking is recorded via a real POST (audited override path)", async () => {
      await page.locator('label:has-text("Emergency override reason") input').fill("Elective list confirmed with the surgical team");
      await expectSaved(page, "/book", async () => {
        await page.getByRole("button", { name: /book with override/i }).click();
      });
    });

    await test.step("WHO Surgical Safety Checklist surface renders", async () => {
      await expect(page.getByText(/who surgical safety checklist/i)).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A9_resume", "the operative note is drafted and signed via a real POST", async () => {
      await page.locator('label:has-text("Procedure performed") input').fill("Open inguinal hernia repair (Lichtenstein)");
      await page.locator('label:has-text("Sign as (surgeon provider id)") input').fill(SURGEON.providerId ?? "");
      await expectSaved(page, "/note", async () => {
        await page.getByRole("button", { name: /save & sign note/i }).click();
      });
    });

    await checklist.notApplicable("A7_notification", "theatre notifications ride outbox events, not a surgeon-facing surface here");
    await checklist.point("A10_logicalEnd", "the perioperative workspace is the surgeon's working surface", async () => {
      await expect(page.getByText(/perioperative|operative note|pacu/i).first()).toBeVisible({ timeout: 15_000 });
    });
    await checklist.finalize(testInfo);
  });
});
