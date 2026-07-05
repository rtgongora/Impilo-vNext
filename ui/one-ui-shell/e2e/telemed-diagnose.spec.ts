import { test } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  provisionTeleconsultSession,
  loginAndOpenSession,
  startVideo,
} from "./session-media-helpers";

/** A/B control for the classroom churn: same instrumentation, telemedicine surface. */

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
    ],
  },
});

test("watch telemedicine churn", async ({ browser, request }) => {
  test.skip(!RUN_PREVIEW, "orchestrated only");
  test.setTimeout(300_000);
  const { sessionId } = await provisionTeleconsultSession(request);
  const context = await browser.newContext({ permissions: ["camera", "microphone"] });
  const page = await loginAndOpenSession(context, MEDIA_PERSONA_A.email, sessionId);
  const t0 = Date.now();
  const stamp = () => `${((Date.now() - t0) / 1000).toFixed(1)}s`;
  page.on("websocket", (ws) => {
    if (!ws.url().includes(":7880")) return;
    console.log(`[ws-open ${stamp()}] token=${(ws.url().split("access_token=")[1] ?? "").slice(0, 25)}`);
    ws.on("close", () => console.log(`[ws-close ${stamp()}]`));
  });
  page.on("console", (msg) => {
    const t = msg.text();
    if (/disconnect|Negotiation|reconnect/i.test(t) && msg.type() !== "debug") {
      console.log(`[console ${stamp()}] ${t.slice(0, 130)}`);
    }
  });
  await startVideo(page, "A");
  for (let i = 0; i < 6; i++) {
    await page.waitForTimeout(10_000);
    const videos = await page.locator("video").count();
    console.log(`[state ${stamp()}] videos=${videos}`);
  }
  await context.close();
});
