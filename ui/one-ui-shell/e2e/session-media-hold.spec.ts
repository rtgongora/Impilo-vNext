import { test, expect } from "@playwright/test";
import { RUN_PREVIEW } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  installMediaHostRewrite,
  loginAndOpenSession,
  seesBothParticipants,
  startVideo,
} from "./session-media-helpers";

/**
 * Media HOLDER for orchestrated proofs (recording, telemetry): joins an
 * EXISTING teleconsult session (MEDIA_HOLD_SESSION_ID env — provisioned by the
 * calling script) with both personas and keeps publishing for
 * MEDIA_HOLD_MS (default 60s) so the orchestrator can drive server-side
 * actions (egress start/stop, admits) against a genuinely active room.
 */

const SESSION_ID = process.env.MEDIA_HOLD_SESSION_ID ?? "";
const HOLD_MS = Number(process.env.MEDIA_HOLD_MS ?? 60_000);

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

test.describe("Session media hold (orchestrated)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
    test.skip(!SESSION_ID, "MEDIA_HOLD_SESSION_ID not provided — this spec only runs orchestrated");
  });

  test("hold two-party media on the given session", async ({ browser }) => {
    test.setTimeout(HOLD_MS + 180_000);

    const contextA = await browser.newContext({ permissions: ["camera", "microphone"] });
    const contextB = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMediaHostRewrite(contextA);
    await installMediaHostRewrite(contextB);

    const pageA = await loginAndOpenSession(contextA, MEDIA_PERSONA_A.email, SESSION_ID);
    const pageB = await loginAndOpenSession(contextB, MEDIA_PERSONA_B.email, SESSION_ID);
    await startVideo(pageA, "holder A");
    await startVideo(pageB, "holder B");

    await expect
      .poll(() => seesBothParticipants(pageA), { timeout: 90_000 })
      .toBe(true);
    console.log("[hold] both participants connected — holding");

    await pageA.waitForTimeout(HOLD_MS);

    expect(await seesBothParticipants(pageA), "media dropped during hold").toBe(true);
    console.log("[hold] hold complete, leaving");
    await contextA.close();
    await contextB.close();
  });
});
