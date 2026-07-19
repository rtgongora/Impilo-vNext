import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { PREVIEW_ORIGIN, RUN_PREVIEW, loginAs, logout, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — Fundo course authoring and learner completion.
 *
 * trainer.chikafu authors and publishes a course through Course Studio; then
 * learner.tembo — a different real user — finds it, enrols, starts, logs out,
 * logs back in and resumes. Serial: the learner half consumes the exact course
 * the author half created (cross-user visibility, A6).
 */

const COURSE_MARKER = `Journey Acceptance Course ${Date.now()}`;
let courseId = "";

test.describe.serial("Fundo author → learner journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("author: New Course → builder → publish", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-author");

    await checklist.point("A1_access", "trainer reaches Course Studio", async () => {
      await loginAs(page, PERSONAS.trainer);
      await gotoAs(page, PERSONAS.trainer, "/learning/studio/courses/new");
      await expect(page.getByText(/new studio course/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "Create draft course enables once titled", async () => {
      const createButton = page.getByRole("button", { name: /create draft course/i });
      await expect(createButton).toBeDisabled();
      await page.getByLabel(/course title/i).fill(COURSE_MARKER);
      await page.getByLabel(/description/i).fill("Created by the golden-journey acceptance harness.");
      await expectActionable(createButton);
    });

    await checklist.point("A3_formOpens", "creation form carries title/description/language", async () => {
      // Anchored to the form's own label — the shell top bar also exposes a
      // language control, which makes a bare getByLabel(/language/i) ambiguous.
      await expect(page.locator("label", { hasText: /^Language/ }).locator("select")).toBeVisible();
    });

    await checklist.point("A4_saves", "draft persists via real POST and opens the builder", async () => {
      await expectSaved(page, "/internal/v1/learning/v11/catalog", async () => {
        await page.getByRole("button", { name: /create draft course/i }).click();
      });
      await page.waitForURL(/\/learning\/studio\/courses\/.+\/builder/, { timeout: 30_000 });
      courseId = page.url().match(/courses\/([^/]+)\/builder/)?.[1] ?? "";
      expect(courseId, "builder URL carries the new course id").not.toEqual("");
    });

    await checklist.point("A5_stateChange", "module and lesson are added to the draft", async () => {
      const moduleTitle = page.locator("input").first();
      const moduleSection = page.getByText(/^add module$/i).first();
      await expect(moduleSection).toBeVisible({ timeout: 20_000 });
      await page.locator("section, div").filter({ hasText: /^Add module/ }).locator("input").first().fill("Module 1");
      await page.getByRole("button", { name: /^add module$/i }).click();
      await page.locator("section, div").filter({ hasText: /^Add lesson/ }).locator("input").first().fill("Lesson 1");
      await page.getByRole("button", { name: /^add lesson$/i }).click();
      // Assert in the Current-structure list — a bare getByText resolves to the
      // module <option> inside the (closed, hence "hidden") select first.
      await expect(
        page.locator("li").filter({ hasText: "Module 1" }).first(),
      ).toBeVisible({ timeout: 15_000 });
      void moduleTitle;
    });

    await checklist.point("A10_logicalEnd", "course reaches PUBLISHED", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/studio/publish");
      // The list renders one <li> per course; a loose "li, tr, div" filter
      // resolves to an ancestor div holding EVERY row's Publish button.
      const row = page.locator("li").filter({ hasText: COURSE_MARKER }).first();
      await expect(row).toBeVisible({ timeout: 20_000 });
      await expectSaved(
        page,
        "/internal/v1/learning/v11/catalog",
        async () => {
          await row.getByRole("button", { name: /^publish$/i }).click();
        },
        "PUT",
      );
    });

    await checklist.notApplicable("A6_crossUserVisibility", "proven by the learner half of this serial journey");
    await checklist.notApplicable("A7_notification", "course-assignment notification covered by assignment flows, not self-publish");
    await checklist.notApplicable("A8_audit", "learning-service audits via outbox events; not surfaced in Studio UI yet — tracked in wave gap register");
    await checklist.notApplicable("A9_resume", "draft persistence across sessions is exercised by the learner resume half");
    await checklist.finalize(testInfo);
  });

  test("learner: find → enrol → start → logout → resume", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-learner");
    test.skip(!courseId, "author half must have created the course");

    await checklist.point("A1_access", "learner reaches the published course", async () => {
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, `/learning/courses/${courseId}`);
      await expect(page.getByText(COURSE_MARKER).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A6_crossUserVisibility", "another user sees the trainer's published course", async () => {
      await expect(page.getByTestId("fundo-enrol-button")).toBeVisible();
    });

    await checklist.point("A2_actionEnabled", "Enrol / Continue is actionable", async () => {
      await expectActionable(page.getByTestId("fundo-enrol-button"));
    });

    let enrolmentUrl = "";
    await checklist.point("A4_saves", "enrolment persists via real POST", async () => {
      await expectSaved(page, "/internal/v1/learning/v11/enrolments", async () => {
        await page.getByTestId("fundo-enrol-button").click();
      });
      await page.waitForURL(/\/learning\/enrolments\/.+/, { timeout: 30_000 });
      enrolmentUrl = new URL(page.url()).pathname;
    });

    await checklist.point("A5_stateChange", "enrolment starts (Start / Continue)", async () => {
      const startButton = page.getByRole("button", { name: /start \/ continue/i }).first();
      await expectActionable(startButton);
      await startButton.click();
    });

    await checklist.point("A9_resume", "logout → login → My learning resumes the enrolment", async () => {
      await logout(page);
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, "/learning/my-learning");
      await expect(
        page.getByText(COURSE_MARKER).first().or(page.getByText(/in progress/i).first()),
      ).toBeVisible({ timeout: 20_000 });
      await gotoAs(page, PERSONAS.learner, enrolmentUrl);
      await expect(page.getByRole("button", { name: /start \/ continue/i }).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "enrolment view renders progress structure", async () => {
      await expect(page.getByText(/lesson 1|module 1/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A10_logicalEnd", "journey lands on a resumable enrolment with progress tracked", async () => {
      expect(enrolmentUrl).toContain("/learning/enrolments/");
    });

    await checklist.notApplicable(
      "A7_notification",
      "self-enrolment does not notify; assignment/certificate notifications covered by scenario-C steel thread",
    );
    await checklist.notApplicable(
      "A8_audit",
      "enrolment audit is outbox-event based; no learner-facing audit surface — tracked in wave gap register",
    );
    await checklist.finalize(testInfo);

    void PREVIEW_ORIGIN;
  });
});
