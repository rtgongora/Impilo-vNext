import { test, expect, type BrowserContext, type Page, type APIRequestContext } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
  apiLogin,
  actorHeaders,
} from "./session-media-helpers";

/**
 * W6 LIVE_EVENT stage flow (orchestrated-only): audience→stage promotion
 * against the live estate.
 *
 *  1. Persona A provisions a PUBLIC_BROADCAST live event (typed scheduling
 *     endpoint; A is creator ⇒ server-resolved HOST tier) and goes live.
 *  2. Persona B token-probe: a client-asserted SPEAKER role request yields a
 *     subscribe-only grant — the minted JWT carries video.canPublish=false
 *     and the response echoes the clamped AUDIENCE role. (The exact
 *     escalation this wave closes.)
 *  3. B opens the room UI: viewer mode, no publish controls, request-stage
 *     button visible. B requests the stage.
 *  4. A opens the backstage producer console, sees B in the speaker queue,
 *     and approves.
 *  5. B's next token mints canPublish=true (SPEAKER) and the room flips to
 *     the stage variant ("On stage").
 *
 * Runs only against the preview estate (PREVIEW_SANDBOX_E2E=1 /
 * PLAYWRIGHT_BASE_URL) with scenario-A personas seeded — orchestrated proof
 * lane, not CI unit e2e.
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

function decodeVideoGrant(token: string): { canPublish?: boolean } {
  const payload = token.split(".")[1] ?? "";
  const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
  const claims = JSON.parse(Buffer.from(normalized, "base64").toString("utf8")) as {
    video?: { canPublish?: boolean };
  };
  return claims.video ?? {};
}

async function loginAndOpen(context: BrowserContext, email: string, path: string): Promise<Page> {
  const page = await context.newPage();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(email);
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);
  const activate = page.getByRole("button", { name: /activate professional profile/i }).first();
  if (await activate.isVisible().catch(() => false)) {
    await activate.evaluate((el) => (el as HTMLButtonElement).click());
    await activate.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
  }
  await page.goto(`${PREVIEW_ORIGIN}${path}`);
  await acceptPoliciesIfGated(page);
  if (!page.url().includes(path)) {
    await page.goto(`${PREVIEW_ORIGIN}${path}`);
  }
  return page;
}

async function mintRoomToken(
  request: APIRequestContext,
  auth: { token: string; anchor: string },
  eventId: string,
  role: string,
  key: string,
  participantId?: string,
) {
  const res = await request.post(`${PREVIEW_ORIGIN}/internal/v1/live/room/${eventId}/token`, {
    headers: actorHeaders(auth, key),
    data: { participantId: participantId ?? auth.anchor, role },
  });
  expect(res.ok(), `room token (${role}): ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return (body.data ?? body) as { accessToken: string; role?: string };
}

test.describe("Live event stage promotion (orchestrated)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("audience cannot publish; requests stage; producer approves; promoted participant publishes", async ({
    browser,
    request,
  }) => {
    test.setTimeout(420_000);
    const run = `live-stage-e2e-${Date.now()}`;

    // 1. Persona A provisions a public broadcast and goes live.
    const a = await apiLogin(request, MEDIA_PERSONA_A.email);
    const b = await apiLogin(request, MEDIA_PERSONA_B.email);

    // The PUBLIC_HEALTH scheduling path refuses orphan events (MISSING_OWNING_ENTITY,
    // by doctrine) — a proof broadcast is a standalone Impilo Live event.
    const createRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/live/events`, {
      headers: actorHeaders(a, `${run}-create`),
      data: {
        title: `Stage proof broadcast ${run}`,
        description: "W6 stage-flow orchestrated proof",
        mode: "PUBLIC_BROADCAST",
        eventType: "BROADCAST",
        owningService: "STANDALONE_IMPILO_LIVE",
        organiserType: "PROVIDER",
        organiserId: "PROV-ZW-00001",
        registrationRequired: false,
        audienceScope: "NATIONAL",
        commentModerationPolicy: "MODERATED",
        replayAllowed: false,
      },
    });
    expect(createRes.ok(), `broadcast create: ${createRes.status()}`).toBeTruthy();
    const created = await createRes.json();
    const eventId = created.id ?? created.data?.id;
    expect(eventId, "live event id").toBeTruthy();

    // Generic-created events start in DRAFT — schedule before going live.
    const scheduleRes = await request.post(
      `${PREVIEW_ORIGIN}/internal/v1/live/events/${eventId}/schedule`,
      { headers: actorHeaders(a, `${run}-schedule`), data: {} },
    );
    expect(scheduleRes.ok(), `schedule: ${scheduleRes.status()}`).toBeTruthy();

    const startRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/live/room/${eventId}/start`, {
      headers: actorHeaders(a, `${run}-start`),
      data: {},
    });
    expect(startRes.ok(), `go live: ${startRes.status()}`).toBeTruthy();

    // B must join once so the room exists for their token probes.
    const joinRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/live/room/${eventId}/join`, {
      headers: actorHeaders(b, `${run}-join-b`),
      data: { participantId: b.anchor, participantType: "PROVIDER", role: "ATTENDEE" },
    });
    expect(joinRes.ok(), `audience join: ${joinRes.status()}`).toBeTruthy();

    // 2. Token probe: client-asserted SPEAKER is clamped to a subscribe-only grant.
    const clampedToken = await mintRoomToken(request, b, eventId, "SPEAKER", `${run}-probe`);
    expect(clampedToken.role).toBe("AUDIENCE");
    expect(decodeVideoGrant(clampedToken.accessToken).canPublish).toBe(false);

    // 3. B opens the room UI as audience: viewer mode, no publish controls, requests stage.
    const contextB = await browser.newContext();
    const pageB = await loginAndOpen(contextB, MEDIA_PERSONA_B.email, `/live/event/${eventId}/room`);
    await expect(pageB.getByTestId("room-tier")).toHaveText(/viewer mode/i, { timeout: 60_000 });
    await expect(pageB.getByRole("button", { name: /^microphone/i })).toHaveCount(0);
    const requestStage = pageB.getByTestId("request-stage-button");
    await expect(requestStage).toBeVisible({ timeout: 30_000 });
    await requestStage.click();
    await expect(pageB.getByTestId("stage-request-pending")).toBeVisible({ timeout: 15_000 });

    // 4. A opens the backstage console and approves B from the speaker queue.
    const contextA = await browser.newContext();
    const pageA = await loginAndOpen(contextA, MEDIA_PERSONA_A.email, `/live/event/${eventId}/backstage`);
    await expect(pageA.getByTestId("speaker-queue")).toBeVisible({ timeout: 60_000 });
    // The stage request is keyed by the participant id the web resolved
    // (provider id when linked, anchor otherwise) — match any pending approve.
    const approve = pageA.locator('[data-testid^="approve-"]').first();
    await expect(approve).toBeVisible({ timeout: 30_000 });
    await approve.click();

    // 5. Promotion is server-truth: B's next token publishes, and the room
    //    flips to the stage variant as the tier poll catches up.
    await expect
      .poll(
        async () => {
          // The browser resolved B's participant id to the linked provider id
          // (PROV-ZW-00007) — the approval is keyed to it, so the promoted
          // token must be minted under the same identity.
          const promoted = await mintRoomToken(
              request, b, eventId, "SPEAKER", `${run}-promoted-${Date.now()}`, "PROV-ZW-00007");
          return promoted.role === "SPEAKER"
            && decodeVideoGrant(promoted.accessToken).canPublish === true;
        },
        { timeout: 30_000, intervals: [2_000] },
      )
      .toBe(true);
    await expect(pageB.getByTestId("room-tier")).toHaveText(/on stage/i, { timeout: 60_000 });

    await contextA.close();
    await contextB.close();
  });
});
