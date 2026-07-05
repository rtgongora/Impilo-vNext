import { test, expect, type APIRequestContext, type BrowserContext } from "@playwright/test";
import { PREVIEW_ORIGIN, RUN_PREVIEW } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
  apiLogin,
  actorHeaders,
  installMediaHostRewrite,
  seesBothParticipants,
} from "./session-media-helpers";

/**
 * LEARNING_LIVE classroom — two-persona live class over the Impilo Live
 * media room (W3 of the adaptive session suite).
 *
 * Flow: API-provision course + LIVE session (learning-service schedules the
 * linked Impilo Live event) + enrol the learner → facilitator (dr.mapfumo)
 * enters /learning/sessions/{id}/classroom in context A, learner
 * (nurse.chienda) in context B → both see the classroom shell and each
 * other's media → facilitator sees the roster panel → sustain 20s.
 *
 * ============================ PROVISIONING-CONTRACT ASSUMPTIONS ============================
 * Written against the PARALLEL W3 backend wave (not yet deployed at authoring time):
 *  1. POST /internal/v1/learning/v11/catalog creates a course and returns
 *     { data: { course: { id } } } (shape tolerated: data.course.id | data.id).
 *  2. POST /internal/v1/learning/v11/sessions accepts
 *     { courseId, title, sessionType: "VIRTUAL", sessionMode: "LIVE", startsAt, endsAt,
 *       facilitator: <dr.mapfumo provider/anchor id>, cpdEnabled } and — when the
 *     live-session integration is enabled — returns
 *     { data: { session: { id, liveEventId | impiloLiveEventId, joinPath | impiloLiveJoinPath } } }.
 *     `sessionMode` is the new W3 field; pre-W3 deployments derive LIVE from sessionType
 *     "VIRTUAL" and still schedule the live event (legacy metadata keys). The spec reads
 *     BOTH key spellings and FAILS EARLY with a clear message when no live event id comes
 *     back (live integration disabled ⇒ no classroom to join).
 *  3. Facilitator role detection on the classroom page compares the session's
 *     `facilitator` free-text id (and/or W3 `facilitators[]`) against the logged-in
 *     user's providerId / learning subject id, so the session is created with
 *     dr.mapfumo's own id in `facilitator`. apiLogin only surfaces `healthId`;
 *     we prefer `user.providerId` when the login payload carries it and fall back
 *     to the health anchor — if role detection still lands on "learner", the
 *     roster-panel assertion below is the honest failure signal.
 *  4. POST /internal/v1/learning/v11/enrolments accepts
 *     { courseId, subjectType, subjectId, enrolmentType: "SELF" } for nurse.chienda.
 *  5. LiveKit fake-media flags mirror the telehealth spec; the media host rewrite
 *     (LIVEKIT_MEDIA_HOST_REWRITE) applies for VM-local hairpin runs.
 * ===========================================================================================
 */

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

interface LoginIdentity {
  token: string;
  anchor: string;
  providerId?: string;
}

/** apiLogin + a second pass for providerId (assumption #3). */
async function loginWithProviderId(request: APIRequestContext, email: string): Promise<LoginIdentity> {
  const res = await request.post(`${PREVIEW_ORIGIN}/internal/v1/auth/login`, {
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-ID": "00000000-0000-4000-8000-000000000001",
      "X-Pod-ID": "pod-e2e",
      "X-Request-ID": crypto.randomUUID(),
      "X-Correlation-ID": `classroom-${Date.now()}`,
      "Idempotency-Key": `classroom-login-${email}-${Date.now()}`,
    },
    data: { email, password: MEDIA_PASSWORD },
  });
  expect(res.ok(), `login ${email}: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  const user = body.data.attributes.user ?? {};
  return {
    token: body.data.attributes.token as string,
    anchor: user.healthId as string,
    providerId: (user.providerId ?? user.provider_id ?? undefined) as string | undefined,
  };
}

/** Provision course + LIVE session (+ learner enrolment); returns ids for the classroom. */
async function provisionLiveClassroom(request: APIRequestContext) {
  const run = `classroom-${Date.now()}`;
  const facilitator = await loginWithProviderId(request, MEDIA_PERSONA_A.email);
  const learner = await loginWithProviderId(request, MEDIA_PERSONA_B.email);
  const facilitatorId = facilitator.providerId ?? facilitator.anchor;

  // 1) Course (studio create — assumption #1).
  const courseRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/learning/v11/catalog`, {
    headers: actorHeaders(facilitator, `${run}-course`),
    data: {
      title: `Classroom proof ${run}`,
      code: `CLSRM-${Date.now()}`,
      description: "LEARNING_LIVE classroom e2e proof course",
      category: "CLINICAL",
      level: "FOUNDATION",
      status: "PUBLISHED",
    },
  });
  expect(courseRes.ok(), `course create: ${courseRes.status()}`).toBeTruthy();
  const courseBody = await courseRes.json();
  const courseId: string =
    courseBody.data?.course?.id ?? courseBody.data?.id ?? courseBody.data?.attributes?.id;
  expect(courseId, "course id").toBeTruthy();

  // 2) LIVE session with a linked Impilo Live event (assumption #2).
  const startsAt = new Date(Date.now() + 60_000).toISOString();
  const endsAt = new Date(Date.now() + 60 * 60_000).toISOString();
  const sessionRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/learning/v11/sessions`, {
    headers: actorHeaders(facilitator, `${run}-session`),
    data: {
      courseId,
      title: `Live class ${run}`,
      sessionType: "VIRTUAL",
      sessionMode: "LIVE",
      startsAt,
      endsAt,
      facilitator: facilitatorId,
      cpdEnabled: false,
    },
  });
  expect(sessionRes.ok(), `session create: ${sessionRes.status()}`).toBeTruthy();
  const sessionBody = await sessionRes.json();
  const session = sessionBody.data?.session ?? sessionBody.data ?? {};
  const sessionId: string = session.id;
  expect(sessionId, "session id").toBeTruthy();
  const liveEventId: string | undefined =
    session.liveEventId ??
    session.impiloLiveEventId ??
    session.metadata?.impiloLiveEventId;
  expect(
    liveEventId,
    "LIVE session must come back with a linked Impilo Live event id " +
      "(liveEventId / impiloLiveEventId). If this is empty the learning-service " +
      "live integration is disabled on this environment — no classroom exists to join.",
  ).toBeTruthy();

  // 3) Enrol the learner (assumption #4).
  const enrolRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/learning/v11/enrolments`, {
    headers: actorHeaders(facilitator, `${run}-enrol`),
    data: {
      courseId,
      subjectType: learner.providerId ? "PROVIDER_PUBLIC_ID" : "USER_HEALTH_ID",
      subjectId: learner.providerId ?? learner.anchor,
      enrolmentType: "SELF",
    },
  });
  expect(enrolRes.ok(), `learner enrolment: ${enrolRes.status()}`).toBeTruthy();

  return { courseId, sessionId, liveEventId, facilitatorId };
}

/** Persona login through the real auth pages, then straight into the classroom. */
async function loginAndOpenClassroom(context: BrowserContext, email: string, sessionId: string) {
  const page = await context.newPage();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(email);
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);

  // Professional surfaces gate on provider activation (one-time store action).
  const activate = page.getByRole("button", { name: /activate professional profile/i }).first();
  if (await activate.isVisible().catch(() => false)) {
    await activate.evaluate((el) => (el as HTMLButtonElement).click());
    await activate.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
  }

  await page.goto(`${PREVIEW_ORIGIN}/learning/sessions/${sessionId}/classroom`);
  await acceptPoliciesIfGated(page);
  return page;
}

test.describe("LEARNING_LIVE classroom — facilitator + learner joint media (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("facilitator and learner share the classroom for 20s; facilitator sees the roster", async ({
    browser,
    request,
  }) => {
    test.setTimeout(240_000);

    const { sessionId } = await provisionLiveClassroom(request);

    const contextA = await browser.newContext();
    const contextB = await browser.newContext();
    await installMediaHostRewrite(contextA);
    await installMediaHostRewrite(contextB);

    try {
      // Context A: facilitator enters the classroom (join also starts the room).
      const facilitatorPage = await loginAndOpenClassroom(contextA, MEDIA_PERSONA_A.email, sessionId);
      await expect(facilitatorPage.getByTestId("classroom-shell")).toBeVisible({ timeout: 45_000 });
      await expect(facilitatorPage.getByTestId("classroom-role")).toHaveText(/facilitator/i, {
        timeout: 30_000,
      });
      // Facilitator rail: the roster (attendance register + check-in code trigger).
      await expect(facilitatorPage.getByTestId("facilitator-roster")).toBeVisible({ timeout: 30_000 });

      // Context B: learner enters the same classroom.
      const learnerPage = await loginAndOpenClassroom(contextB, MEDIA_PERSONA_B.email, sessionId);
      await expect(learnerPage.getByTestId("classroom-shell")).toBeVisible({ timeout: 45_000 });
      await expect(learnerPage.getByTestId("learner-rail")).toBeVisible({ timeout: 30_000 });

      // Both parties subscribe to each other's media in the classroom stage.
      await expect
        .poll(async () => seesBothParticipants(facilitatorPage), {
          timeout: 90_000,
          message: "facilitator should see both participants",
        })
        .toBe(true);
      await expect
        .poll(async () => seesBothParticipants(learnerPage), {
          timeout: 90_000,
          message: "learner should see both participants",
        })
        .toBe(true);

      // Sustain: the class holds for 20s with both parties still in the room.
      await facilitatorPage.waitForTimeout(20_000);
      expect(await seesBothParticipants(facilitatorPage), "facilitator view after 20s sustain").toBe(true);
      expect(await seesBothParticipants(learnerPage), "learner view after 20s sustain").toBe(true);

      // The learner's raise-hand seam stays honest: disabled until a realtime publish path exists.
      await expect(learnerPage.getByTestId("raise-hand-button")).toBeDisabled();
    } finally {
      await contextA.close();
      await contextB.close();
    }
  });
});
