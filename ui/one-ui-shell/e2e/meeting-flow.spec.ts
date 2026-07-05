import { test, expect, type BrowserContext, type Page } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
  apiLogin,
  actorHeaders,
  seesBothParticipants,
} from "./session-media-helpers";

/**
 * W5 MEETING flow (orchestrated-only): the full KNOCK-lobby journey against the live estate.
 *
 *  1. Persona A provisions a meeting via the BFF (Khuluma MEETING conversation over an
 *     Impilo Live event, rtc-gateway MEETING-template media room).
 *  2. A (host) opens /meet/{id} — auto-admitted (roomAdmin bypass), grid room mounts,
 *     the host screen-share control is visible.
 *  3. B (participant) opens /meet/{id} — lands in the KNOCK lobby (WAITING).
 *  4. A admits B from the participants panel's admit queue.
 *  5. B's join poll flips to READY, the room mounts, and both parties publish
 *     (fake media devices) until each layout shows both participants.
 *
 * Runs only against the preview estate (PREVIEW_SANDBOX_E2E=1 / PLAYWRIGHT_BASE_URL) with
 * the scenario-A personas seeded — it is part of the orchestrated proof lane, not CI unit e2e.
 */

const REWRITE = process.env.LIVEKIT_MEDIA_HOST_REWRITE ?? ""; // "from=to" (VM-local hairpin only)

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

/** Meeting joins mint tokens on the khuluma meeting/join path — rewrite the LiveKit host there too. */
async function installMeetingMediaRewrite(context: BrowserContext) {
  if (!REWRITE.includes("=")) return;
  const [from, to] = REWRITE.split("=");
  await context.route("**/meeting/join", async (route) => {
    const response = await route.fetch();
    const text = (await response.text()).replaceAll(from, to);
    await route.fulfill({ response, body: text });
  });
}

async function loginAndOpenMeeting(context: BrowserContext, email: string, meetingId: string): Promise<Page> {
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
  await page.goto(`${PREVIEW_ORIGIN}/meet/${meetingId}`);
  await acceptPoliciesIfGated(page);
  if (!page.url().includes(`/meet/${meetingId}`)) {
    await page.goto(`${PREVIEW_ORIGIN}/meet/${meetingId}`);
  }
  return page;
}

test.describe("Meeting lobby flow (orchestrated)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("host admits a knocking participant; both publish; host can screen-share", async ({ browser, request }) => {
    test.setTimeout(420_000);
    const run = `meet-e2e-${Date.now()}`;

    // 1. Provision the meeting as persona A with B invited as a member.
    const a = await apiLogin(request, MEDIA_PERSONA_A.email);
    const b = await apiLogin(request, MEDIA_PERSONA_B.email);
    const createRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/khuluma/meetings`, {
      headers: actorHeaders(a, `${run}-create`),
      data: {
        title: `W5 meeting proof ${run}`,
        participants: [
          { actorId: b.anchor, actorType: "PROVIDER", displayName: MEDIA_PERSONA_B.name, role: "MEMBER" },
        ],
      },
    });
    expect(createRes.ok(), `meeting create: ${createRes.status()}`).toBeTruthy();
    const created = await createRes.json();
    const meetingId: string = created.conversationId;
    expect(meetingId, "meeting conversation id").toBeTruthy();

    const contextA = await browser.newContext({ permissions: ["camera", "microphone"] });
    const contextB = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMeetingMediaRewrite(contextA);
    await installMeetingMediaRewrite(contextB);

    // 2. Host joins: roomAdmin bypasses the lobby, the grid room mounts.
    const pageA = await loginAndOpenMeeting(contextA, MEDIA_PERSONA_A.email, meetingId);
    await expect(pageA.getByTestId("meeting-room-shell")).toBeVisible({ timeout: 60_000 });
    expect(await pageA.getByTestId("meeting-room-shell").getAttribute("data-role")).toBe("HOST");
    // Host screen-share control is granted by the MEETING template.
    await expect(
      pageA.locator('button[title*="screen" i], .lk-button[data-lk-source="screen_share"], button:has-text("Share screen")').first(),
    ).toBeVisible({ timeout: 30_000 });

    // 3. Participant knocks and waits in the lobby.
    const pageB = await loginAndOpenMeeting(contextB, MEDIA_PERSONA_B.email, meetingId);
    await expect(pageB.getByTestId("meeting-lobby-waiting")).toBeVisible({ timeout: 60_000 });

    // 4. Host sees the admit queue (4s lobby poll) and admits B.
    await pageA.getByTestId("tab-participants").click().catch(() => undefined);
    await expect(pageA.getByTestId("admit-queue")).toBeVisible({ timeout: 30_000 });
    await pageA.getByTestId(`admit-${b.anchor}`).click();

    // 5. B's join poll flips READY and the room mounts; both parties end up publishing.
    await expect(pageB.getByTestId("meeting-room-shell")).toBeVisible({ timeout: 60_000 });
    await expect
      .poll(async () => (await seesBothParticipants(pageA)) && (await seesBothParticipants(pageB)), {
        timeout: 120_000,
        intervals: [2_000],
      })
      .toBe(true);

    // Lobby mirror reflects the decision on the summary surface.
    await pageA.goto(`${PREVIEW_ORIGIN}/meet/${meetingId}/summary`);
    await expect(pageA.getByTestId(`attendance-${b.anchor}`)).toContainText("ADMITTED", { timeout: 30_000 });

    await contextA.close();
    await contextB.close();
  });
});
