import { test, expect, type Page } from "@playwright/test";
import { RUN_PREVIEW } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  installMediaHostRewrite,
  loginAndOpenSession,
  provisionTeleconsultSession,
  seesBothParticipants,
  startVideo,
} from "./session-media-helpers";

/**
 * Session-media core proof — two REAL browser contexts join one governed
 * teleconsult room over LiveKit and each must subscribe to the other's video
 * track. This is the substrate proof for the adaptive session suite: BFF-minted
 * tokens only, real signal (7880) and real media (7881/tcp, 7882/udp).
 *
 * Setup is API-first (scripts/e2e/scenario-a-clinical-journey.sh phase-9
 * contract): dr.mapfumo creates → routes → consents → submits; nurse.chienda
 * accepts from the pool — both are then legitimate media participants.
 *
 * VM-local runs may set LIVEKIT_MEDIA_HOST_REWRITE="from=to" (hairpin note in
 * session-media-helpers.ts).
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

test.describe("Session media core — two-party LiveKit join (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("both participants publish and subscribe real media", async ({ browser, request }) => {
    test.setTimeout(240_000);
    const { sessionId } = await provisionTeleconsultSession(request);

    const contextA = await browser.newContext({ permissions: ["camera", "microphone"] });
    const contextB = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMediaHostRewrite(contextA);
    await installMediaHostRewrite(contextB);

    const pageA = await loginAndOpenSession(contextA, MEDIA_PERSONA_A.email, sessionId);
    const pageB = await loginAndOpenSession(contextB, MEDIA_PERSONA_B.email, sessionId);

    await startVideo(pageA, "provider A");
    await startVideo(pageB, "provider B");

    // TrackSubscribed proof: each context must know BOTH participants' camera
    // tracks (tile count or the pager total — the legacy 160px strip paginates).
    for (const [label, page] of [
      ["provider A", pageA],
      ["provider B", pageB],
    ] as const) {
      await expect
        .poll(() => seesBothParticipants(page), {
          message: `${label}: waiting for both participants' tracks in the layout`,
          timeout: 90_000,
        })
        .toBe(true);
      await expect
        .poll(async () => page.locator(".lk-participant-tile video").count(), {
          message: `${label}: waiting for a live video element`,
          timeout: 60_000,
        })
        .toBeGreaterThanOrEqual(1);
    }

    // Sustain: the historic failure mode was PEER_CONNECTION_DISCONNECTED at
    // ~15-20s (ICE consent expiry over broken paths). Hold the call well past
    // that window and require both rooms to still see each other.
    await pageA.waitForTimeout(45_000);
    for (const [label, page] of [
      ["provider A", pageA],
      ["provider B", pageB],
    ] as const) {
      expect(await seesBothParticipants(page as Page), `${label}: media dropped during sustain window`).toBe(true);
    }

    await contextA.close();
    await contextB.close();
  });
});
