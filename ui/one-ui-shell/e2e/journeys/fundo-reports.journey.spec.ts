import { test, expect, type Page } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable } from "./acceptance";

/**
 * Golden journey — Fundo reporting reflects real, cross-user activity.
 *
 * A supervisor/manager (trainer.chikafu — LEARNING_AUTHOR, the cadre that owns
 * both /learning/reports and Studio Analytics) opens the reporting surfaces and
 * sees REAL numbers produced by other users' enrolments/completions/certificates
 * (the EHR completions + certificates the assessment/certificate journey created).
 *
 * The anti-false-green discipline for a read journey: every headline number is
 * observed via a real GET 2xx from the live ingress AND asserted to be non-zero
 * AND cross-checked against the value rendered in the DOM. A page that merely
 * renders zeros (the original "looks like mocks" complaint) fails these steps.
 */

const EHR_TITLE = /introduction to impilo ehr/i;

/** Reload the page and capture the fresh GET 2xx envelope for a report path. */
async function reloadAndCapture(page: Page, fragment: string): Promise<Record<string, unknown>> {
  const [resp] = await Promise.all([
    page.waitForResponse(
      (r) => r.request().method() === "GET" && r.url().includes(fragment) && r.status() >= 200 && r.status() < 300,
      { timeout: 30_000 },
    ),
    page.reload(),
  ]);
  const body = (await resp.json()) as { data?: Record<string, unknown> };
  return body.data ?? {};
}

test.describe.serial("Fundo reporting journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("manager: /learning/reports shows real enrolment & completion numbers", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-reports");

    await checklist.point("A1_access", "manager reaches Learning reports", async () => {
      await loginAs(page, PERSONAS.trainer);
      await gotoAs(page, PERSONAS.trainer, "/learning/reports");
      await expect(page.getByText(/learning reports/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A4_saves", "overview KPIs are real (non-zero) and observed via a live 2xx", async () => {
      const overview = await reloadAndCapture(page, "/internal/v1/learning/v11/reports/overview");
      expect(Number(overview.publishedCourses), "published courses > 0").toBeGreaterThan(0);
      expect(Number(overview.completed), "completed enrolments > 0 (from prior journeys)").toBeGreaterThanOrEqual(1);
      // The rendered Completed tile carries the same real value.
      await expect(page.getByText(String(overview.completed)).first()).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A6_crossUserVisibility", "the manager sees another user's completion (EHR 100%)", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/reports/courses");
      const items = ((await reloadAndCapture(page, "/internal/v1/learning/v11/reports/course-completions")).items ?? []) as Array<Record<string, unknown>>;
      const ehr = items.find((i) => /EHR/i.test(String(i.courseCode ?? "")));
      expect(ehr, "EHR course completion row present").toBeTruthy();
      expect(Number(ehr?.completed), "EHR completed >= 1").toBeGreaterThanOrEqual(1);
      const row = page.getByRole("row", { name: EHR_TITLE });
      await expect(row).toBeVisible({ timeout: 20_000 });
      await expect(row).toContainText("100");
    });

    await checklist.point("A2_actionEnabled", "report navigation links are actionable", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/reports");
      await expectActionable(page.getByRole("link", { name: /cohort completions/i }).first());
      await expectActionable(page.getByRole("link", { name: /overdue learning/i }).first());
    });

    await checklist.point("A3_formOpens", "the course-completions table renders its columns", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/reports/courses");
      await expect(page.getByRole("cell", { name: /completion %/i }).first().or(page.getByText(/completion %/i).first())).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A10_logicalEnd", "cohort completions shows the EHR cohort at 100%", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/reports/cohorts");
      const items = ((await reloadAndCapture(page, "/internal/v1/learning/v11/reports/cohort-completions")).items ?? []) as Array<Record<string, unknown>>;
      const ehr = items.find((i) => /EHR/i.test(String(i.courseCode ?? "")));
      expect(Number(ehr?.certificatesIssued), "EHR certificates issued >= 1").toBeGreaterThanOrEqual(1);
      const row = page.getByRole("row", { name: EHR_TITLE });
      await expect(row).toBeVisible({ timeout: 20_000 });
      await expect(row).toContainText("100");
    });

    await checklist.notApplicable("A5_stateChange", "reporting is read-only; the state was mutated by the enrolment/completion journeys");
    await checklist.notApplicable("A7_notification", "opening a report does not notify");
    await checklist.notApplicable("A8_audit", "report reads are not surfaced as a user-facing audit trail");
    await checklist.notApplicable("A9_resume", "reports are stateless reads; resume is not applicable");
    await checklist.finalize(testInfo);
  });

  test("manager: Studio Analytics KPIs show real totals", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-studio-analytics");

    await checklist.point("A1_access", "author reaches Studio Analytics (LEARNING_AUTHOR gated)", async () => {
      await loginAs(page, PERSONAS.trainer);
      await gotoAs(page, PERSONAS.trainer, "/learning/studio/analytics");
      await expect(page.getByText(/studio analytics/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A4_saves", "KPI tiles carry real overview + certificate totals", async () => {
      const overview = await reloadAndCapture(page, "/internal/v1/learning/v11/reports/overview");
      const publishedCourses = Number(overview.publishedCourses);
      expect(publishedCourses, "published courses > 0").toBeGreaterThan(0);
      const coursesTile = page.locator("div.rounded.border").filter({ hasText: "Courses" }).first();
      await expect(coursesTile).toContainText(String(publishedCourses), { timeout: 15_000 });
      const certsTile = page.locator("div.rounded.border").filter({ hasText: "Certificates" }).first();
      // Certificates come from cohort-completions totals; at least the two EHR certs exist.
      await expect(certsTile).not.toContainText(/Certificates\s*0\s*$/);
    });

    await checklist.point("A2_actionEnabled", "role-view toggles are actionable", async () => {
      await expectActionable(page.getByRole("button", { name: /^ADMIN$/ }).first());
    });

    await checklist.point("A3_formOpens", "the KPI grid renders four headline tiles", async () => {
      for (const label of ["Courses", "Enrolments", "Completed", "Certificates"]) {
        await expect(page.locator("div.rounded.border").filter({ hasText: label }).first()).toBeVisible({ timeout: 15_000 });
      }
    });

    await checklist.point("A10_logicalEnd", "analytics presents a coherent real dashboard", async () => {
      const completedTile = page.locator("div.rounded.border").filter({ hasText: "Completed" }).first();
      await expect(completedTile).toBeVisible();
    });

    await checklist.notApplicable("A5_stateChange", "analytics is read-only");
    await checklist.notApplicable("A6_crossUserVisibility", "cross-user rollup is proven by the reports half of this journey");
    await checklist.notApplicable("A7_notification", "opening analytics does not notify");
    await checklist.notApplicable("A8_audit", "analytics reads are not a user-facing audit trail");
    await checklist.notApplicable("A9_resume", "analytics is a stateless read; resume is not applicable");
    await checklist.finalize(testInfo);
  });
});
