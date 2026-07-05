import { test, expect } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_B,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
} from "./session-media-helpers";

/**
 * Course-player proof for the W4 LEARNING_RECORDING wave (orchestrated-only,
 * like classroom-media-hold.spec.ts): a learner opens a lesson whose live
 * replay was adopted as a governed media asset, the CoursePlayer mounts with a
 * signed playback URL, playback accrues watch progress (debounced upserts) and
 * the resume position survives a reload.
 *
 * Requires the estate orchestration to provision:
 *   COURSE_PLAYER_ENROLMENT_ID — an active enrolment for MEDIA_PERSONA_B
 *   COURSE_PLAYER_LESSON_ID    — a VIDEO lesson with an attached media asset
 *                                (replay pipeline: scripts/e2e/learning-live-proof.sh
 *                                 through PUBLISHED_REPLAY, or an authored asset)
 */

const ENROLMENT_ID = process.env.COURSE_PLAYER_ENROLMENT_ID ?? "";
const LESSON_ID = process.env.COURSE_PLAYER_LESSON_ID ?? "";
const WATCH_MS = Number(process.env.COURSE_PLAYER_WATCH_MS ?? 25_000);

test.use({
  launchOptions: {
    args: ["--autoplay-policy=no-user-gesture-required"],
  },
});

async function loginAndOpenLesson(page: import("@playwright/test").Page) {
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(MEDIA_PERSONA_B.email);
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);
  await page.goto(`${PREVIEW_ORIGIN}/learning/enrolments/${ENROLMENT_ID}/lessons/${LESSON_ID}`);
  await acceptPoliciesIfGated(page);
}

test.describe("Course player (orchestrated)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
    test.skip(
      !ENROLMENT_ID || !LESSON_ID,
      "COURSE_PLAYER_ENROLMENT_ID / COURSE_PLAYER_LESSON_ID not provided — this spec only runs orchestrated",
    );
  });

  test("learner plays a governed recording, progress accrues, resume survives reload", async ({ page }) => {
    test.setTimeout(WATCH_MS + 240_000);

    await loginAndOpenLesson(page);

    // The governed player mounts (not the legacy iframe/native fallback).
    await expect(page.getByTestId("course-player")).toBeVisible({ timeout: 60_000 });
    const video = page.getByTestId("course-player-video");
    await expect(video).toBeVisible();
    const src = await video.getAttribute("src");
    expect(src, "signed playback URL should be minted").toBeTruthy();
    expect(src!.startsWith("http")).toBe(true);

    // Start playback and hold for the watch window so debounced upserts land.
    await page.getByTestId("player-play-pause").click();
    await expect
      .poll(
        async () => video.evaluate((el) => (el as HTMLVideoElement).currentTime),
        { message: "playback should advance", timeout: 30_000 },
      )
      .toBeGreaterThan(1);
    await page.waitForTimeout(WATCH_MS);

    // Completion banner reflects accrued percent (in-progress or completed).
    const banner = page.getByTestId("player-completion-banner");
    await expect(banner).toBeVisible();

    // Drop a bookmark at the current position and expect it listed.
    await page.getByTestId("player-bookmark-note").fill("e2e checkpoint");
    await page.getByTestId("player-bookmark-add").click();
    await expect(page.getByTestId("player-bookmark-item").first()).toBeVisible({ timeout: 15_000 });

    // Note the playhead, reload, and expect resume ≥ that point (furthest position).
    const positionBeforeReload = await video.evaluate((el) => (el as HTMLVideoElement).currentTime);
    await page.reload();
    await acceptPoliciesIfGated(page);
    await expect(page.getByTestId("course-player-video")).toBeVisible({ timeout: 60_000 });
    await expect
      .poll(
        async () =>
          page
            .getByTestId("course-player-video")
            .evaluate((el) => (el as HTMLVideoElement).currentTime),
        { message: "player should resume from the furthest watched position", timeout: 30_000 },
      )
      .toBeGreaterThanOrEqual(Math.max(0, positionBeforeReload - 15));
  });
});
