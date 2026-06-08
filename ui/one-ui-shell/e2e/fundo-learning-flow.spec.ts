import { test, expect } from "./fixtures";
import { FUNDO_E2E, installFundoLearningMocks } from "./fundo-learning-mocks";

test.describe("Fundo / learning journey", () => {
  test.beforeEach(async ({ page }) => {
    await installFundoLearningMocks(page);
  });

  test("learning hub shows Fundo orchestration rail", async ({ page }) => {
    await page.goto("/learning");
    await expect(page.locator('[data-testid="fundo-learning-orchestration-rail"]')).toBeVisible();
    await expect(page.getByTestId("fundo-catalog-probe")).toContainText(/published course/i);
  });

  test("full learner journey: catalog → enrol → lessons → assessment → certificate → CPD evidence", async ({ page }) => {
    await page.goto("/learning/catalog");
    await expect(page.getByRole("heading", { name: "Impilo Fundo Catalogue" })).toBeVisible();
    await page.getByRole("link", { name: FUNDO_E2E.courseTitle }).click();
    await expect(page).toHaveURL(new RegExp(`/learning/courses/${FUNDO_E2E.courseId}`));

    await page.getByTestId("fundo-enrol-button").click();
    await expect(page).toHaveURL(new RegExp(`/learning/enrolments/${FUNDO_E2E.enrolmentId}`));

    for (const lessonId of FUNDO_E2E.lessonIds) {
      await page.goto(`/learning/enrolments/${FUNDO_E2E.enrolmentId}/lessons/${lessonId}`);
      await expect(page.getByTestId("fundo-lesson-content")).toBeVisible();
      await page.getByTestId("fundo-lesson-open").click();
      await page.getByTestId("fundo-lesson-mark-complete").click();
    }

    await page.goto(`/learning/enrolments/${FUNDO_E2E.enrolmentId}`);
    await expect(page.getByText(/100%/)).toBeVisible();
    await page.getByTestId("fundo-issue-certificate").click();

    await page.goto(`/learning/assessments/${FUNDO_E2E.assessmentId}/attempt`);
    await page.locator("select").nth(0).selectOption("true");
    await page.locator("select").nth(1).selectOption("Ctrl+K");
    await page.getByTestId("fundo-assessment-submit").click();
    await expect(page.getByRole("link", { name: /View attempt result/i })).toBeVisible();

    await page.goto("/learning/certificates");
    await expect(page.getByTestId("fundo-certificate-list")).toContainText("FUNDO-CERT-E2E-001");

    await page.goto("/learning/cpd");
    await expect(page.locator("body")).toContainText(FUNDO_E2E.courseTitle);
    await expect(page.locator("body")).toContainText(/council handoff/i);
    await expect(page.locator("body")).toContainText(/PENDING_REVIEW/i);
  });

  test("survey respond records feedback without placeholder chrome", async ({ page }) => {
    await page.goto(`/learning/surveys/${FUNDO_E2E.surveyActivityId}/respond`);
    await expect(page.getByTestId("fundo-survey-respond")).toBeVisible();
    await expect(page.getByText(/fallback survey placeholder/i)).toHaveCount(0);
    await page.getByTestId("fundo-survey-response").fill("The EHR orientation was clear and practical.");
    await page.getByTestId("fundo-survey-submit").click();
    await expect(page.getByTestId("fundo-survey-success")).toBeVisible();
  });

  test("lesson player renders embedded video content", async ({ page }) => {
    await page.goto(`/learning/enrolments/${FUNDO_E2E.enrolmentId}/lessons/${FUNDO_E2E.lessonIds[0]}`);
    await expect(page.getByTestId("fundo-lesson-video-embed")).toBeVisible();
  });
});
