import { test, expect } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PERSONA_B,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
  installMediaHostRewrite,
} from "./session-media-helpers";

/**
 * Classroom media HOLDER for the W3 learning-live proof: both personas open
 * an EXISTING learning session's classroom (CLASSROOM_SESSION_ID env,
 * provisioned by scripts/e2e/learning-live-proof.sh), join the live-event
 * room through the classroom shell, and keep publishing for CLASSROOM_HOLD_MS
 * so webhook-accurate attendance accrues. Leaving at the end drives the
 * event-completion chain (room finished → event ENDED → policy completion).
 */

const SESSION_ID = process.env.CLASSROOM_SESSION_ID ?? "";
const HOLD_MS = Number(process.env.CLASSROOM_HOLD_MS ?? 100_000);

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

async function loginAndOpenClassroom(
  context: import("@playwright/test").BrowserContext,
  email: string,
) {
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
  await page.goto(`${PREVIEW_ORIGIN}/learning/sessions/${SESSION_ID}/classroom`);
  await acceptPoliciesIfGated(page);
  // The work shell can open the Switch Context dialog over the classroom at
  // any point after activation — run a dismissal watchdog for the page's
  // lifetime: whenever the dialog appears, close it (the classroom itself
  // does not require a work-session context to join the live room).
  const dismissSwitchContext = async () => {
    // The dialog is the work-context demand — satisfy it like a real user:
    // Start Work Session → pick the seeded facility → confirm whatever step
    // follows. Closing it is futile (the guard re-opens it).
    const heading = page.getByRole("heading", { name: /switch context/i }).first();
    if (await heading.isVisible().catch(() => false)) {
      const startWork = page.getByRole("button", { name: /start work session/i }).first();
      if (await startWork.isVisible().catch(() => false)) {
        await startWork.evaluate((el) => (el as HTMLButtonElement).click()).catch(() => {});
        await page.waitForTimeout(800);
      }
    }
    const facilityOption = page.getByText(/harare central/i).first();
    if (await facilityOption.isVisible().catch(() => false)) {
      await facilityOption.evaluate((el) => (el as HTMLElement).click()).catch(() => {});
      await page.waitForTimeout(500);
    }
    for (const label of [/^(start|confirm|continue|enter)( session| work)?$/i]) {
      const btn = page.getByRole("button", { name: label }).first();
      if (await btn.isVisible().catch(() => false)) {
        await btn.evaluate((el) => (el as HTMLButtonElement).click()).catch(() => {});
      }
    }
  };
  const watchdog = setInterval(() => void dismissSwitchContext(), 2_000);
  page.on("close", () => clearInterval(watchdog));
  await dismissSwitchContext();
  // Ensure we are actually on the classroom after any context redirects.
  if (!page.url().includes("/classroom")) {
    await page.goto(`${PREVIEW_ORIGIN}/learning/sessions/${SESSION_ID}/classroom`);
  }
  return page;
}

test.describe("Classroom media hold (orchestrated)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
    test.skip(!SESSION_ID, "CLASSROOM_SESSION_ID not provided — this spec only runs orchestrated");
  });

  test("facilitator + learner hold classroom media", async ({ browser }) => {
    test.setTimeout(HOLD_MS + 240_000);

    const contextA = await browser.newContext({ permissions: ["camera", "microphone"] });
    const contextB = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMediaHostRewrite(contextA);
    await installMediaHostRewrite(contextB);

    const pageA = await loginAndOpenClassroom(contextA, MEDIA_PERSONA_A.email);
    const pageB = await loginAndOpenClassroom(contextB, MEDIA_PERSONA_B.email);

    // The classroom shell joins the live event room on entry; wait until each
    // page renders LiveKit participant tiles for both parties.
    for (const [label, page] of [["facilitator", pageA], ["learner", pageB]] as const) {
      await expect
        .poll(
          async () => {
            const tiles = await page.locator(".lk-participant-tile").count();
            if (tiles >= 2) return true;
            const pager = await page.getByText(/\bof\s+2\b/).count();
            return tiles >= 1 && pager >= 1;
          },
          { message: `${label}: waiting for both participants in the classroom`, timeout: 120_000 },
        )
        .toBe(true);
    }
    console.log("[classroom-hold] both connected — holding");

    await pageA.waitForTimeout(HOLD_MS);
    console.log("[classroom-hold] hold complete, leaving");

    await contextA.close();
    await contextB.close();
  });
});
