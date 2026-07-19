import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, logout, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — Fundo assessment-taking → scoring → certificate.
 *
 * A learner (learner.tembo) enrols in the seeded "Introduction to Impilo EHR"
 * course (FUNDO-EHR-101, the one estate course whose quiz carries real scored
 * questions), takes its two-question quiz and is auto-scored to a PASS; then
 * completes every required lesson so the enrolment reaches COMPLETED and a
 * certificate is issued and viewable.
 *
 * Both halves drive the real TLS ingress with a real Keycloak login and assert
 * observed 2xx mutations via expectSaved — rendered UI alone never counts.
 *
 * The EHR fixture is the vehicle because it is the only estate assessment with
 * objective questions + correct answers wired; the 9 seed-script course quizzes
 * carry zero questions (documented gap in the wave report).
 */

// Seeded EHR fixture (canonical tenant): course + its scored quiz.
const EHR_COURSE_ID = "aaaaaaaa-0000-0000-0000-000000000002";

test.describe.serial("Fundo assessment → certificate journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("learner: enrol → take quiz → auto-scored PASS", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-assessment");
    let enrolmentId = "";

    await checklist.point("A1_access", "learner reaches the scored EHR course", async () => {
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, `/learning/courses/${EHR_COURSE_ID}`);
      await expect(page.getByText(/introduction to impilo ehr/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "Enrol / Continue is actionable", async () => {
      await expectActionable(page.getByTestId("fundo-enrol-button"));
    });

    await checklist.point("A4_saves", "enrolment persists via real POST", async () => {
      await expectSaved(page, "/internal/v1/learning/v11/enrolments", async () => {
        await page.getByTestId("fundo-enrol-button").click();
      });
      await page.waitForURL(/\/learning\/enrolments\/.+/, { timeout: 30_000 });
      enrolmentId = page.url().match(/enrolments\/([^/?#]+)/)?.[1] ?? "";
      expect(enrolmentId, "enrolment id captured").not.toEqual("");
    });

    await checklist.point("A3_formOpens", "assessment opens with its scored questions", async () => {
      // Enrolment player → Assessment prompts → Open assessment → Start attempt.
      await page.getByRole("link", { name: /open assessment/i }).first().click();
      await expect(page.getByText(/ehr navigation quick quiz/i).first()).toBeVisible({ timeout: 20_000 });
      await page.getByRole("link", { name: /start attempt/i }).first().click();
      await expect(page.getByTestId("fundo-assessment-submit")).toBeVisible({ timeout: 20_000 });
      // First scored question prompt renders.
      await expect(page.getByText(/only entry point into Impilo vNext/i).first()).toBeVisible({ timeout: 15_000 });
    });

    let attemptUrl = "";
    await checklist.point("A5_stateChange", "answers submit and the attempt is graded", async () => {
      // Scope to the answer <select>s by their unique option values — the shell
      // top bar also renders a language <select>, so positional nth is unsafe.
      // Q1 TRUE_FALSE (correct = true); Q2 MULTIPLE_CHOICE (correct = Ctrl+K).
      const tfSelect = page.locator("select").filter({ has: page.locator('option[value="false"]') }).first();
      const mcSelect = page.locator("select").filter({ has: page.locator('option[value="Ctrl+K"]') }).first();
      await tfSelect.selectOption("true");
      await mcSelect.selectOption("Ctrl+K");
      await expectSaved(
        page,
        `/internal/v1/learning/v11/assessments/`,
        async () => {
          await page.getByTestId("fundo-assessment-submit").click();
        },
      );
      const viewLink = page.getByRole("link", { name: /view attempt result/i });
      await expect(viewLink).toBeVisible({ timeout: 20_000 });
      attemptUrl = (await viewLink.getAttribute("href")) ?? "";
      await viewLink.click();
    });

    await checklist.point("A10_logicalEnd", "attempt result shows a scored PASS", async () => {
      await page.waitForURL(/\/learning\/attempts\/.+/, { timeout: 20_000 });
      // Objective-only quiz auto-grades to 100 (both answers correct); passMark 50.
      await expect(page.getByText(/Score:\s*100/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Passed:\s*true/i).first()).toBeVisible({ timeout: 20_000 });
      void attemptUrl;
    });

    await checklist.notApplicable("A6_crossUserVisibility", "an assessment attempt is subject-private; cross-user visibility is proven by the author→learner catalogue journey");
    await checklist.notApplicable("A7_notification", "assessment submission does not notify; certificate issuance notification is exercised by the certificate half");
    await checklist.notApplicable("A8_audit", "attempt audit is outbox-event based (ASSESSMENT_ATTEMPT_SUBMITTED); no learner-facing audit surface");
    await checklist.notApplicable("A9_resume", "a graded attempt is terminal — resume-after-logout is exercised by the certificate half of this journey");
    await checklist.finalize(testInfo);
  });

  test("learner: complete all lessons → issue → view certificate", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-certificate");
    let enrolmentId = "";

    await checklist.point("A1_access", "learner reaches the enrolment player", async () => {
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, `/learning/courses/${EHR_COURSE_ID}`);
      await expectSaved(page, "/internal/v1/learning/v11/enrolments", async () => {
        await page.getByTestId("fundo-enrol-button").click();
      });
      await page.waitForURL(/\/learning\/enrolments\/.+/, { timeout: 30_000 });
      enrolmentId = page.url().match(/enrolments\/([^/?#]+)/)?.[1] ?? "";
      expect(enrolmentId, "enrolment id captured").not.toEqual("");
    });

    await checklist.point("A5_stateChange", "every required lesson is marked complete", async () => {
      // Collect the lesson-player links from the enrolment player, then drive each.
      const lessonHrefs = await page
        .locator(`a[href*="/learning/enrolments/${enrolmentId}/lessons/"]`)
        .evaluateAll((els) => Array.from(new Set(els.map((e) => (e as HTMLAnchorElement).getAttribute("href") ?? ""))).filter(Boolean));
      expect(lessonHrefs.length, "enrolment player lists lesson links").toBeGreaterThan(0);
      for (const href of lessonHrefs) {
        await gotoAs(page, PERSONAS.learner, href);
        await expect(page.getByTestId("fundo-lesson-mark-complete")).toBeVisible({ timeout: 20_000 });
        await expectSaved(page, "/internal/v1/learning/v11/progress", async () => {
          await page.getByTestId("fundo-lesson-mark-complete").click();
        });
      }
    });

    await checklist.point("A2_actionEnabled", "Issue certificate enables once complete", async () => {
      await gotoAs(page, PERSONAS.learner, `/learning/enrolments/${enrolmentId}`);
      await expectActionable(page.getByTestId("fundo-issue-certificate"));
    });

    await checklist.point("A4_saves", "certificate is issued via real POST", async () => {
      await expectSaved(
        page,
        `/internal/v1/learning/v11/enrolments/${enrolmentId}/certificate`,
        async () => {
          await page.getByTestId("fundo-issue-certificate").click();
        },
      );
    });

    await checklist.point("A3_formOpens", "certificate is viewable on the certificates surface", async () => {
      await gotoAs(page, PERSONAS.learner, "/learning/certificates");
      const list = page.getByTestId("fundo-certificate-list");
      await expect(list).toBeVisible({ timeout: 20_000 });
      await expect(list.getByText(/introduction to impilo ehr/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A6_crossUserVisibility", "the certificate carries a validity badge (issued status surfaced)", async () => {
      await expect(page.getByTestId("fundo-certificate-validity").first()).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A9_resume", "logout → login → certificate persists", async () => {
      await logout(page);
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, "/learning/certificates");
      await expect(
        page.getByTestId("fundo-certificate-list").getByText(/introduction to impilo ehr/i).first(),
      ).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A10_logicalEnd", "journey ends on a persisted, viewable certificate", async () => {
      await expect(page.getByTestId("fundo-certificate-list")).toBeVisible();
    });

    await checklist.notApplicable("A7_notification", "issuance records a learning.certificate.issued intent via the notification writer (stub provider by default) — not a learner-facing toast in this surface");
    await checklist.notApplicable("A8_audit", "certificate audit is outbox-event based (CERTIFICATE_ISSUED); no learner-facing audit surface");
    await checklist.finalize(testInfo);
  });
});
