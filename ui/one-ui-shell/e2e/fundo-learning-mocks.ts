import type { Page } from "@playwright/test";

export const FUNDO_E2E = {
  courseId: "aaaaaaaa-0000-0000-0000-000000000002",
  courseCode: "FUNDO-EHR-101",
  courseTitle: "Introduction to Impilo EHR",
  enrolmentId: "enr-e2e-fundo-001",
  lessonIds: [
    "cccccccc-0002-0001-0000-000000000001",
    "cccccccc-0002-0002-0000-000000000001",
    "cccccccc-0002-0002-0000-000000000002",
  ],
  assessmentId: "dddddddd-0002-0000-0000-000000000001",
  attemptId: "att-e2e-fundo-001",
  certificateId: "cert-e2e-fundo-001",
  surveyActivityId: "survey-e2e-fundo-001",
  subjectType: "PROVIDER_PUBLIC_ID",
  subjectId: "PRV-E2E-001",
} as const;

type ProgressRow = { lessonId: string; progressPercent: number };

export async function installFundoLearningMocks(page: Page) {
  const progress = new Map<string, number>();
  let enrolmentStatus = "ENROLLED";
  let certificateIssued = false;
  let attemptSubmitted = false;

  const json = (body: unknown) => ({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body),
  });

  const structure = {
    id: FUNDO_E2E.courseId,
    code: FUNDO_E2E.courseCode,
    title: FUNDO_E2E.courseTitle,
    description: "Working with the Impilo vNext One Experience Shell across roles.",
    category: "EHR_BASICS",
    level: "INTRODUCTORY",
    cpdEligible: true,
    mandatory: true,
    estimatedDurationMinutes: 60,
    modules: [
      {
        id: "bbbbbbbb-0002-0000-0000-000000000001",
        title: "The One Experience Shell",
        sequence: 1,
        lessons: [
          {
            id: FUNDO_E2E.lessonIds[0],
            title: "Navigating the shell",
            contentType: "VIDEO",
            contentRef: "https://www.youtube.com/watch?v=ImpiloEhrIntro",
            sequence: 1,
            required: true,
            estimatedDurationMinutes: 20,
          },
        ],
      },
      {
        id: "bbbbbbbb-0002-0000-0000-000000000002",
        title: "Search, start, and clinical tools",
        sequence: 2,
        lessons: [
          {
            id: FUNDO_E2E.lessonIds[1],
            title: "Search and start command",
            contentType: "TEXT",
            contentBody: "Use Ctrl+K to open the command palette.",
            sequence: 1,
            required: true,
            estimatedDurationMinutes: 20,
          },
          {
            id: FUNDO_E2E.lessonIds[2],
            title: "Clinical tools overview",
            contentType: "PRACTICAL_TASK",
            contentBody: '["Open a patient chart","Launch a clinical workflow"]',
            sequence: 2,
            required: true,
            estimatedDurationMinutes: 15,
          },
        ],
      },
    ],
  };

  await page.route("**/internal/v1/learning/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/.*\/internal\/v1\/learning/, "");
    const method = route.request().method();

    if (method === "GET" && path === "/v11/catalog") {
      return route.fulfill(
        json({
          data: {
            limit: 50,
            items: [
              {
                id: FUNDO_E2E.courseId,
                code: FUNDO_E2E.courseCode,
                title: FUNDO_E2E.courseTitle,
                category: "EHR_BASICS",
                level: "INTRODUCTORY",
                cpdEligible: true,
                mandatory: true,
                status: "PUBLISHED",
              },
            ],
          },
        }),
      );
    }

    if (method === "GET" && path === `/v11/courses/${FUNDO_E2E.courseId}/structure`) {
      return route.fulfill(json({ data: { structure } }));
    }

    if (method === "GET" && path.startsWith("/v11/my-learning")) {
      const completedLessons = [...progress.entries()].filter(([, pct]) => pct >= 100).length;
      return route.fulfill(
        json({
          data: {
            inProgress: completedLessons > 0 && completedLessons < FUNDO_E2E.lessonIds.length ? [{ enrolmentId: FUNDO_E2E.enrolmentId }] : [],
            required: [{ courseId: FUNDO_E2E.courseId }],
            overdue: [],
            cpdEligibleCompletions: certificateIssued ? [{ courseId: FUNDO_E2E.courseId }] : [],
            certificates: certificateIssued ? [{ id: FUNDO_E2E.certificateId }] : [],
            completed: completedLessons === FUNDO_E2E.lessonIds.length ? [{ enrolmentId: FUNDO_E2E.enrolmentId }] : [],
          },
        }),
      );
    }

    if (method === "GET" && path.startsWith("/v11/enrolments?")) {
      return route.fulfill(
        json({
          data: [{ id: FUNDO_E2E.enrolmentId, courseId: FUNDO_E2E.courseId, status: enrolmentStatus }],
        }),
      );
    }

    if (method === "GET" && path === `/v11/enrolments/${FUNDO_E2E.enrolmentId}`) {
      return route.fulfill(
        json({
          data: {
            enrolment: {
              id: FUNDO_E2E.enrolmentId,
              courseId: FUNDO_E2E.courseId,
              status: enrolmentStatus,
              subjectType: FUNDO_E2E.subjectType,
              subjectId: FUNDO_E2E.subjectId,
            },
          },
        }),
      );
    }

    if (method === "GET" && path === `/v11/enrolments/${FUNDO_E2E.enrolmentId}/progress`) {
      const items: ProgressRow[] = FUNDO_E2E.lessonIds
        .filter((lessonId) => progress.has(lessonId))
        .map((lessonId) => ({ lessonId, progressPercent: progress.get(lessonId) ?? 0 }));
      return route.fulfill(json({ data: { items } }));
    }

    if (method === "POST" && path === "/v11/enrolments") {
      enrolmentStatus = "ENROLLED";
      return route.fulfill(
        json({
          data: {
            enrolment: {
              id: FUNDO_E2E.enrolmentId,
              courseId: FUNDO_E2E.courseId,
              status: enrolmentStatus,
            },
          },
        }),
      );
    }

    if (method === "POST" && path === `/v11/enrolments/${FUNDO_E2E.enrolmentId}/start`) {
      enrolmentStatus = "IN_PROGRESS";
      return route.fulfill(json({ data: { status: "IN_PROGRESS" } }));
    }

    if (method === "POST" && path.startsWith("/v11/lessons/") && path.endsWith("/open")) {
      return route.fulfill(json({ data: { status: "OPENED" } }));
    }

    if (method === "POST" && path === "/v11/progress") {
      const body = route.request().postDataJSON() as { lessonId?: string; progressPercent?: number };
      if (body.lessonId) progress.set(body.lessonId, Number(body.progressPercent ?? 100));
      if (progress.size >= FUNDO_E2E.lessonIds.length) enrolmentStatus = "COMPLETED";
      return route.fulfill(json({ data: { status: "RECORDED" } }));
    }

    if (method === "GET" && path === `/v11/courses/${FUNDO_E2E.courseId}/assessments`) {
      return route.fulfill(
        json({
          data: {
            items: [
              {
                id: FUNDO_E2E.assessmentId,
                title: "EHR navigation quick quiz",
                assessmentType: "QUIZ",
              },
            ],
          },
        }),
      );
    }

    if (method === "GET" && path === `/v11/assessments/${FUNDO_E2E.assessmentId}`) {
      return route.fulfill(
        json({
          data: {
            assessment: {
              id: FUNDO_E2E.assessmentId,
              title: "EHR navigation quick quiz",
              maxAttempts: 3,
              questions: [
                { id: "q1", type: "TRUE_FALSE", prompt: "The One Experience Shell is the only entry point into Impilo vNext." },
                { id: "q2", type: "MULTIPLE_CHOICE", prompt: "Which command surfaces a global start workflow in the shell?", optionsJson: '["Tab","Ctrl+K","Esc","Shift"]' },
              ],
            },
          },
        }),
      );
    }

    if (method === "POST" && path === `/v11/assessments/${FUNDO_E2E.assessmentId}/attempts`) {
      attemptSubmitted = true;
      return route.fulfill(
        json({
          data: {
            attempt: {
              id: FUNDO_E2E.attemptId,
              passed: true,
              score: 100,
            },
          },
        }),
      );
    }

    if (method === "GET" && path === `/v11/attempts/${FUNDO_E2E.attemptId}`) {
      return route.fulfill(
        json({
          data: {
            attempt: {
              id: FUNDO_E2E.attemptId,
              passed: true,
              score: 100,
              reviewState: "AUTO_SCORED",
            },
          },
        }),
      );
    }

    if (method === "POST" && path === `/v11/enrolments/${FUNDO_E2E.enrolmentId}/certificate`) {
      certificateIssued = true;
      enrolmentStatus = "COMPLETED";
      return route.fulfill(
        json({
          data: {
            certificate: {
              id: FUNDO_E2E.certificateId,
              certificateNumber: "FUNDO-CERT-E2E-001",
              title: FUNDO_E2E.courseTitle,
            },
          },
        }),
      );
    }

    if (method === "GET" && path.startsWith("/v11/certificates?")) {
      return route.fulfill(
        json({
          data: {
            items: certificateIssued
              ? [
                  {
                    id: FUNDO_E2E.certificateId,
                    certificateNumber: "FUNDO-CERT-E2E-001",
                    title: FUNDO_E2E.courseTitle,
                  },
                ]
              : [],
          },
        }),
      );
    }

    if (method === "GET" && path.startsWith("/v11/cpd/evidence")) {
      return route.fulfill(
        json({
          data: {
            items: certificateIssued
              ? [
                  {
                    courseId: FUNDO_E2E.courseId,
                    courseTitle: FUNDO_E2E.courseTitle,
                    certificateId: FUNDO_E2E.certificateId,
                    completedAt: new Date().toISOString(),
                    verifiedState: "PENDING_REVIEW",
                  },
                ]
              : [],
          },
        }),
      );
    }

    if (method === "GET" && path.startsWith("/v11/interactive/activities")) {
      return route.fulfill(
        json({
          data: {
            items: [
              {
                id: FUNDO_E2E.surveyActivityId,
                activityType: "SURVEY",
                title: "Post-course feedback",
              },
            ],
          },
        }),
      );
    }

    if (method === "POST" && path === `/v11/interactive/activities/${FUNDO_E2E.surveyActivityId}/responses`) {
      return route.fulfill(json({ data: { status: "RECORDED" } }));
    }

    if (method === "GET" && path.startsWith("/v11/pathways")) {
      return route.fulfill(json({ data: { items: [] } }));
    }

    return route.fulfill(json({ data: {} }));
  });
}
