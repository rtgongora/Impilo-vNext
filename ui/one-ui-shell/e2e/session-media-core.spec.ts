import { test, expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { PREVIEW_ORIGIN, PREVIEW_TENANT_ID, RUN_PREVIEW } from "./preview-sandbox-helpers";

/**
 * Session-media core proof — two REAL browser contexts join one governed
 * teleconsult room over LiveKit and each must subscribe to the other's video
 * track. This is the substrate proof for the adaptive session suite: BFF-minted
 * tokens only, real signal (7880) and real media (7881/tcp, 7882/udp).
 *
 * Setup is API-first (same contract as scripts/e2e/scenario-a-clinical-journey.sh
 * phase 9): dr.mapfumo creates → routes → consents → submits; nurse.chienda
 * accepts from the pool — both are then legitimate media participants.
 *
 * Hairpin note: the preview VM cannot reach its own public IP, so VM-local runs
 * set LIVEKIT_MEDIA_HOST_REWRITE="41.57.127.235=10.50.1.67" — the spec rewrites
 * the room_url host in the media-token response via route interception (test-side
 * only; external runs omit the env and exercise the true public path).
 */

const PERSONA_A = { email: "dr.mapfumo", name: "Dr Mapfumo" };
const PERSONA_B = { email: "nurse.chienda", name: "Nurse Chienda" };
const PASSWORD = process.env.SCENARIO_A_PASSWORD ?? "ImpiloTest123!";
const REWRITE = process.env.LIVEKIT_MEDIA_HOST_REWRITE ?? ""; // "from=to"

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

function trustHeaders(extra: Record<string, string> = {}) {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": PREVIEW_TENANT_ID,
    "X-Pod-ID": "pod-e2e",
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": `media-core-${Date.now()}`,
    ...extra,
  };
}

async function apiLogin(request: APIRequestContext, email: string) {
  const res = await request.post(`${PREVIEW_ORIGIN}/internal/v1/auth/login`, {
    headers: trustHeaders({ "Idempotency-Key": `mc-login-${email}-${Date.now()}` }),
    data: { email, password: PASSWORD },
  });
  expect(res.ok(), `login ${email}: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return {
    token: body.data.attributes.token as string,
    anchor: body.data.attributes.user.healthId as string,
  };
}

const FACILITY_ID = "f1000000-0000-0000-0000-000000000001"; // Harare Central (scenario-A seed)

function actorHeaders(auth: { token: string; anchor: string }, key: string) {
  return trustHeaders({
    "X-Actor-ID": auth.anchor,
    "X-Actor-Type": "PROVIDER",
    "X-Purpose-Of-Use": "TREATMENT",
    "X-Facility-ID": FACILITY_ID,
    Authorization: `Bearer ${auth.token}`,
    "Idempotency-Key": key,
  });
}

/** Provision a teleconsult that both personas may join with media. */
async function provisionSession(request: APIRequestContext) {
  const run = `mediacore-${Date.now()}`;
  const a = await apiLogin(request, PERSONA_A.email);
  const b = await apiLogin(request, PERSONA_B.email);

  const patientRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/patients`, {
    headers: actorHeaders(a, `${run}-patient`),
    data: {
      given_name: "MediaCore",
      family_name: run,
      date_of_birth: "1991-03-03",
      sex: "FEMALE",
      phone: `+263772${String(Date.now()).slice(-6)}`,
      facility_id: FACILITY_ID,
    },
  });
  expect(patientRes.ok(), `patient create: ${patientRes.status()}`).toBeTruthy();
  const patientBody = await patientRes.json();
  const cpid =
    patientBody.data?.attributes?.cpid ??
    patientBody.data?.attributes?.patient_id ??
    patientBody.data?.id;
  expect(cpid, "walk-in CPID").toBeTruthy();

  const teleRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/teleconsult/sessions`, {
    headers: actorHeaders(a, `${run}-tele`),
    data: {
      patientId: cpid,
      urgency: "routine",
      specialty: "GENERAL_MEDICINE",
      clinicalQuestion: "Session-media core two-party proof",
      virtualMode: "video",
    },
  });
  expect(teleRes.ok(), `teleconsult create: ${teleRes.status()}`).toBeTruthy();
  const teleBody = await teleRes.json();
  const sessionId =
    teleBody.data?.referralId ?? teleBody.data?.id ?? teleBody.data?.referral_id;
  expect(sessionId, "teleconsult session id").toBeTruthy();

  const route = await request.put(
    `${PREVIEW_ORIGIN}/internal/v1/teleconsult/sessions/${sessionId}/referral`,
    {
      headers: actorHeaders(a, `${run}-route`),
      data: {
        routingType: "SPECIALTY_POOL",
        routingTarget: "GENERAL_MEDICINE",
        clinicalQuestion: "Session-media core two-party proof",
      },
    },
  );
  expect(route.ok(), `routing: ${route.status()}`).toBeTruthy();

  await request.post(`${PREVIEW_ORIGIN}/internal/v1/teleconsult/sessions/${sessionId}/consent`, {
    headers: actorHeaders(a, `${run}-consent`),
    data: { patientId: cpid, type: "TELEMEDICINE" },
  });
  await request.post(`${PREVIEW_ORIGIN}/internal/v1/teleconsult/sessions/${sessionId}/submit`, {
    headers: actorHeaders(a, `${run}-submit`),
    data: {},
  });
  const accept = await request.post(
    `${PREVIEW_ORIGIN}/internal/v1/teleconsult/sessions/${sessionId}/accept`,
    { headers: actorHeaders(b, `${run}-accept`), data: {} },
  );
  expect(accept.ok(), `pool accept by ${PERSONA_B.email}: ${accept.status()}`).toBeTruthy();

  return { sessionId };
}

/** Rewrite the LiveKit host in media-token responses (VM-local hairpin only). */
async function installMediaHostRewrite(context: BrowserContext) {
  if (!REWRITE.includes("=")) return;
  const [from, to] = REWRITE.split("=");
  await context.route("**/media/token", async (route) => {
    const response = await route.fetch();
    const text = (await response.text()).replaceAll(from, to);
    await route.fulfill({ response, body: text });
  });
}

async function acceptPoliciesIfGated(page: Page) {
  const gate = page.getByText(/review our policies/i).first();
  const gated = await gate
    .waitFor({ state: "visible", timeout: 5_000 })
    .then(() => true)
    .catch(() => false);
  if (!gated) return;
  for (const box of await page.locator('input[type="checkbox"]').all()) {
    await box.check();
  }
  await page.getByRole("button", { name: /accept and continue/i }).click();
  await gate.waitFor({ state: "hidden", timeout: 15_000 });
}

async function loginAndOpenSession(context: BrowserContext, email: string, sessionId: string) {
  const page = await context.newPage();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(email);
  await page.locator("#password").fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);

  // Professional surfaces are gated on provider activation (one-time store action).
  const activate = page.getByRole("button", { name: /activate professional profile/i }).first();
  if (await activate.isVisible().catch(() => false)) {
    // The banner lives in a sidebar drawer that can sit outside the viewport —
    // dispatch the click on the DOM node directly.
    await activate.evaluate((el) => (el as HTMLButtonElement).click());
    await activate.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
  }

  await page.goto(`${PREVIEW_ORIGIN}/telemedicine/session/${sessionId}`);
  await acceptPoliciesIfGated(page);
  return page;
}

async function startVideo(page: Page, label: string) {
  page.on("response", (res) => {
    if (res.url().includes("/media/token")) {
      console.log(`[${label}] media/token → ${res.status()}`);
    }
  });
  // Call controls: phone first, video second. Pre-token both are titled
  // "Waiting for governed RTC media"; the click itself fetches the token.
  const videoButton = page
    .locator('button[title="Video call"], button[title="Waiting for governed RTC media"]')
    .last();
  await expect(videoButton).toBeVisible({ timeout: 30_000 });
  await videoButton.click();
}

test.describe("Session media core — two-party LiveKit join (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("both participants publish and subscribe real media", async ({ browser, request }) => {
    test.setTimeout(240_000);
    const { sessionId } = await provisionSession(request);

    const contextA = await browser.newContext({ permissions: ["camera", "microphone"] });
    const contextB = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMediaHostRewrite(contextA);
    await installMediaHostRewrite(contextB);

    const pageA = await loginAndOpenSession(contextA, PERSONA_A.email, sessionId);
    const pageB = await loginAndOpenSession(contextB, PERSONA_B.email, sessionId);

    await startVideo(pageA, "provider A");
    await startVideo(pageB, "provider B");

    // TrackSubscribed proof: each context renders BOTH participant tiles with
    // live <video> elements — the remote one only exists if subscription of the
    // other side's published track succeeded end-to-end (signal + SRTP media).
    for (const [label, page] of [
      ["provider A", pageA],
      ["provider B", pageB],
    ] as const) {
      await expect
        .poll(async () => page.locator(".lk-participant-tile").count(), {
          message: `${label}: waiting for local+remote participant tiles`,
          timeout: 90_000,
        })
        .toBeGreaterThanOrEqual(2);
      await expect
        .poll(async () => page.locator(".lk-participant-tile video").count(), {
          message: `${label}: waiting for live video elements in tiles`,
          timeout: 60_000,
        })
        .toBeGreaterThanOrEqual(2);
    }

    await contextA.close();
    await contextB.close();
  });
});
