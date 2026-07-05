import { expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { PREVIEW_ORIGIN, PREVIEW_TENANT_ID } from "./preview-sandbox-helpers";

/**
 * Shared helpers for real-media session specs (session-media-core,
 * session-media-hold, recording proofs): API provisioning of a governed
 * teleconsult, persona login through the real auth pages, and the video-start
 * click path on the provider session workspace.
 */

export const MEDIA_PERSONA_A = { email: "dr.mapfumo", name: "Dr Mapfumo" };
export const MEDIA_PERSONA_B = { email: "nurse.chienda", name: "Nurse Chienda" };
export const MEDIA_PASSWORD = process.env.SCENARIO_A_PASSWORD ?? "ImpiloTest123!";
export const MEDIA_FACILITY_ID = "f1000000-0000-0000-0000-000000000001"; // Harare Central (scenario-A seed)

const REWRITE = process.env.LIVEKIT_MEDIA_HOST_REWRITE ?? ""; // "from=to"

export function trustHeaders(extra: Record<string, string> = {}) {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": PREVIEW_TENANT_ID,
    "X-Pod-ID": "pod-e2e",
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": `media-${Date.now()}`,
    ...extra,
  };
}

export async function apiLogin(request: APIRequestContext, email: string) {
  const res = await request.post(`${PREVIEW_ORIGIN}/internal/v1/auth/login`, {
    headers: trustHeaders({ "Idempotency-Key": `mh-login-${email}-${Date.now()}` }),
    data: { email, password: MEDIA_PASSWORD },
  });
  expect(res.ok(), `login ${email}: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  return {
    token: body.data.attributes.token as string,
    anchor: body.data.attributes.user.healthId as string,
  };
}

export function actorHeaders(auth: { token: string; anchor: string }, key: string) {
  return trustHeaders({
    "X-Actor-ID": auth.anchor,
    "X-Actor-Type": "PROVIDER",
    "X-Purpose-Of-Use": "TREATMENT",
    "X-Facility-ID": MEDIA_FACILITY_ID,
    Authorization: `Bearer ${auth.token}`,
    "Idempotency-Key": key,
  });
}

/** Provision a teleconsult both media personas may join (create→route→consent→submit→pool-accept). */
export async function provisionTeleconsultSession(request: APIRequestContext) {
  const run = `mediaprov-${Date.now()}`;
  const a = await apiLogin(request, MEDIA_PERSONA_A.email);
  const b = await apiLogin(request, MEDIA_PERSONA_B.email);

  const patientRes = await request.post(`${PREVIEW_ORIGIN}/internal/v1/patients`, {
    headers: actorHeaders(a, `${run}-patient`),
    data: {
      given_name: "MediaProof",
      family_name: run,
      date_of_birth: "1991-03-03",
      sex: "FEMALE",
      phone: `+263772${String(Date.now()).slice(-6)}`,
      facility_id: MEDIA_FACILITY_ID,
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
      clinicalQuestion: "Session media proof",
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
        clinicalQuestion: "Session media proof",
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
  expect(accept.ok(), `pool accept: ${accept.status()}`).toBeTruthy();

  return { sessionId, cpid };
}

/** Rewrite the LiveKit host in media-token responses (VM-local hairpin only). */
export async function installMediaHostRewrite(context: BrowserContext) {
  if (!REWRITE.includes("=")) return;
  const [from, to] = REWRITE.split("=");
  const rewrite = async (route: import("@playwright/test").Route) => {
    const response = await route.fetch();
    const text = (await response.text()).replaceAll(from, to);
    await route.fulfill({ response, body: text });
  };
  // Teleconsult media tokens + live-room tokens (classroom, live events).
  await context.route("**/media/token", rewrite);
  await context.route("**/room/*/token", rewrite);
}

export async function acceptPoliciesIfGated(page: Page) {
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

export async function loginAndOpenSession(context: BrowserContext, email: string, sessionId: string) {
  const page = await context.newPage();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(email);
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);

  // Professional surfaces are gated on provider activation (one-time store action).
  const activate = page.getByRole("button", { name: /activate professional profile/i }).first();
  if (await activate.isVisible().catch(() => false)) {
    await activate.evaluate((el) => (el as HTMLButtonElement).click());
    await activate.waitFor({ state: "hidden", timeout: 10_000 }).catch(() => {});
  }

  await page.goto(`${PREVIEW_ORIGIN}/telemedicine/session/${sessionId}`);
  await acceptPoliciesIfGated(page);
  return page;
}

export async function startVideo(page: Page, label: string) {
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

/** True when the layout knows both participants (tile count or the "1 of 2" pager). */
export async function seesBothParticipants(page: Page) {
  const tiles = await page.locator(".lk-participant-tile").count();
  if (tiles >= 2) return true;
  const pager = await page.getByText(/\bof\s+2\b/).count();
  return tiles >= 1 && pager >= 1;
}
