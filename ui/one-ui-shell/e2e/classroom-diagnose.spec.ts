import { test } from "@playwright/test";
import { RUN_PREVIEW, PREVIEW_ORIGIN } from "./preview-sandbox-helpers";
import { MEDIA_PASSWORD, acceptPoliciesIfGated } from "./session-media-helpers";

/** One-shot diagnostic: watch the classroom for 70s, log token fetches + WS churn. */
const SESSION_ID = process.env.CLASSROOM_SESSION_ID ?? "";

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
    ],
  },
});

test("watch classroom churn", async ({ browser }) => {
  test.skip(!RUN_PREVIEW || !SESSION_ID, "orchestrated only");
  test.setTimeout(220_000);
  const context = await browser.newContext({ permissions: ["camera", "microphone"] });
  const page = await context.newPage();
  const t0 = Date.now();
  const stamp = () => `${((Date.now() - t0) / 1000).toFixed(1)}s`;

  page.on("request", (req) => {
    const u = req.url();
    if (/\/token(\?|$)/.test(u) || u.includes("/join")) {
      console.log(`[req ${stamp()}] ${req.method()} ${u.split("?")[0].slice(-70)}`);
    }
  });
  page.on("websocket", (ws) => {
    if (!ws.url().includes(":7880")) return;
    console.log(`[ws-open ${stamp()}] token=${(ws.url().split("access_token=")[1] ?? "").slice(0, 25)}`);
    ws.on("close", () => console.log(`[ws-close ${stamp()}]`));
  });
  page.on("console", (msg) => {
    const t = msg.text();
    if (/disconnect|Negotiation|reconnect/i.test(t) && msg.type() !== "debug") {
      console.log(`[console ${stamp()}] ${t.slice(0, 140)}`);
    }
  });

  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill("dr.mapfumo");
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);
  const activate = page.getByRole("button", { name: /activate professional profile/i }).first();
  if (await activate.isVisible().catch(() => false)) {
    await activate.evaluate((el) => (el as HTMLButtonElement).click());
    await page.waitForTimeout(1500);
  }
  await page.goto(`${PREVIEW_ORIGIN}/learning/sessions/${SESSION_ID}/classroom`);
  await acceptPoliciesIfGated(page);

  for (let i = 0; i < 7; i++) {
    await page.waitForTimeout(10_000);
    const stage = await page.getByTestId("classroom-stage").count();
    const videos = await page.locator("video").count();
    const spinner = await page.getByText(/Connecting to the governed classroom/i).count();
    const unavailable = await page.getByText(/not available yet/i).count();
    console.log(`[state ${stamp()}] stage=${stage} videos=${videos} connecting=${spinner} unavailable=${unavailable} url=${page.url().slice(-30)}`);
  }
  await context.close();
});
