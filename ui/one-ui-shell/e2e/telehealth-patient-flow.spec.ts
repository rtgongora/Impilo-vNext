import { test, expect, type BrowserContext } from "@playwright/test";
import { PREVIEW_ORIGIN, RUN_PREVIEW } from "./preview-sandbox-helpers";
import {
  MEDIA_PERSONA_A,
  MEDIA_PASSWORD,
  acceptPoliciesIfGated,
  installMediaHostRewrite,
  loginAndOpenSession,
  provisionTeleconsultSession,
  seesBothParticipants,
  startVideo,
} from "./session-media-helpers";

/**
 * Telehealth patient flow — two-persona waiting-room proof.
 *
 * Context A: provider (dr.mapfumo) on the session workspace, with the
 * waiting-room admit control and the front-and-centre video stage.
 * Context B: patient on /my/telehealth/{id} — device check → ask to join →
 * WAITING (no token) → provider admits → auto-transition to the consult →
 * both parties subscribe to each other's media → sustain 30s.
 *
 * Patient persona: citizen.moyo — the seeded CITIZEN login from
 * tools/auth/impilo-realm.json (CPID-ZW-00001, password ImpiloTest123!),
 * reconciled into the preview Keycloak by
 * scripts/operator/reconcile-keycloak-realm-users.sh. Role comes from the
 * page (the /my surface always requests role PATIENT), not the persona.
 */

const CITIZEN_PERSONA = { email: "citizen.moyo", name: "Tatenda Moyo" };

test.use({
  launchOptions: {
    args: [
      "--use-fake-device-for-media-stream",
      "--use-fake-ui-for-media-stream",
      "--autoplay-policy=no-user-gesture-required",
    ],
  },
});

async function loginCitizenAndOpenVisit(context: BrowserContext, sessionId: string) {
  const page = await context.newPage();
  await page.goto(`${PREVIEW_ORIGIN}/auth/login`);
  await page.locator("#identifier").fill(CITIZEN_PERSONA.email);
  await page.locator("#password").fill(MEDIA_PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith("/auth"), { timeout: 30_000 });
  await acceptPoliciesIfGated(page);
  await page.goto(`${PREVIEW_ORIGIN}/my/telehealth/${sessionId}`);
  await acceptPoliciesIfGated(page);
  return page;
}

test.describe("Telehealth patient flow — waiting room, admit, joint media (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("patient waits, provider admits, both see each other for 30s", async ({ browser, request }) => {
    test.setTimeout(300_000);
    const { sessionId } = await provisionTeleconsultSession(request);

    const providerContext = await browser.newContext({ permissions: ["camera", "microphone"] });
    const patientContext = await browser.newContext({ permissions: ["camera", "microphone"] });
    await installMediaHostRewrite(providerContext);
    await installMediaHostRewrite(patientContext);

    // ── Context A: provider opens the session workspace ──
    const providerPage = await loginAndOpenSession(providerContext, MEDIA_PERSONA_A.email, sessionId);
    await expect(providerPage.getByTestId("waiting-room-admit-control")).toBeVisible({ timeout: 30_000 });

    // Provider goes live first so the room has a publisher when the patient lands.
    await startVideo(providerPage, "provider");
    await expect(providerPage.getByTestId("session-video-stage")).toBeVisible({ timeout: 60_000 });

    // ── Context B: patient opens the citizen visit page ──
    const patientPage = await loginCitizenAndOpenVisit(patientContext, sessionId);
    await expect(patientPage.getByTestId("patient-waiting-room")).toBeVisible({ timeout: 30_000 });

    // Device check → ask to join. The first token answer must be WAITING (no token).
    const gatedTokenResponse = patientPage.waitForResponse(
      (res) => res.url().includes("/media/token") && res.request().method() === "POST",
      { timeout: 60_000 },
    );
    await patientPage.getByRole("button", { name: /i'm ready — join the visit/i }).click();
    const gatedBody = await (await gatedTokenResponse).json().catch(() => ({} as Record<string, unknown>));
    const gatedData = (gatedBody?.data ?? {}) as Record<string, unknown>;

    // Negative proof: before admit the patient holds no media token.
    expect(String(gatedData.status ?? "").toUpperCase()).toBe("WAITING");
    expect(gatedData.token ?? "").toBeFalsy();
    await expect(patientPage.getByTestId("patient-waiting-message")).toBeVisible({ timeout: 15_000 });
    expect(await patientPage.locator(".lk-participant-tile").count()).toBe(0);

    // ── Provider console shows the waiting patient (5s poll) and admits ──
    await expect(providerPage.getByTestId("waiting-room-entry").first()).toBeVisible({ timeout: 45_000 });
    await expect(providerPage.getByTestId("waiting-room-count")).not.toHaveText("0");
    await providerPage.getByRole("button", { name: /admit/i }).first().click();

    // ── Patient auto-transitions to the consult (poll ≤5s + connect) ──
    await expect(patientPage.getByTestId("patient-consult-view")).toBeVisible({ timeout: 60_000 });

    // Both parties must know both participants' tracks.
    for (const [label, page] of [
      ["provider", providerPage],
      ["patient", patientPage],
    ] as const) {
      await expect
        .poll(() => seesBothParticipants(page), {
          message: `${label}: waiting for both participants in the layout`,
          timeout: 90_000,
        })
        .toBe(true);
    }

    // Sustain the call for 30s and require both rooms to still see each other.
    await patientPage.waitForTimeout(30_000);
    for (const [label, page] of [
      ["provider", providerPage],
      ["patient", patientPage],
    ] as const) {
      expect(await seesBothParticipants(page), `${label}: media dropped during sustain window`).toBe(true);
    }

    await providerContext.close();
    await patientContext.close();
  });
});
