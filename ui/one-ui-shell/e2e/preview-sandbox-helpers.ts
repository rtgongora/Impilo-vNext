import { test, type BrowserContext, type Page } from "@playwright/test";

export const PREVIEW_ORIGIN = process.env.PLAYWRIGHT_BASE_URL ?? "https://impilo.mohcc.gov.zw";

/** Canonical dev tenant — matches ui/one-ui-shell api-client default. */
export const PREVIEW_TENANT_ID = "00000000-0000-4000-8000-000000000001";

/** Harare Central — aligned with inventory-service V002 preview seeds. */
export const PREVIEW_FACILITY_ID = "a1b2c3d4-0001-4000-8000-000000000001";

export const RUN_PREVIEW =
  process.env.PREVIEW_SANDBOX_E2E === "1" ||
  /41\.57\.127\.235|127\.0\.0\.1|localhost|impilo\.mohcc\.gov\.zw/.test(PREVIEW_ORIGIN);

/** Seeded SYSTEM_ADMIN — covers enterprise / governance / registry proofs. */
export const PREVIEW_PERSONA = {
  username: process.env.PREVIEW_E2E_PERSONA ?? "admin.central",
  password: process.env.PREVIEW_E2E_PASSWORD ?? process.env.PERSONA_PASSWORD ?? "ImpiloTest123!",
};

export const PREVIEW_USER = {
  id: "preview-persist-e2e",
  name: "Preview Persist E2E",
  displayName: "Preview Persist E2E",
  email: "preview-persist@impilo.zw",
  roles: ["SYSTEM_ADMIN", "CLINICIAN", "ADMIN"],
  actorType: "PROVIDER" as const,
  providerId: "PRV-PREVIEW-E2E",
  providerActivated: true,
  assuranceLevel: "VERIFIED" as const,
};

export const PREVIEW_FACILITY = {
  id: PREVIEW_FACILITY_ID,
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB"],
};

/**
 * Client-side facility/consent context only. Authentication is the opaque
 * `__Host-impilo_session` cookie established by {@link loginPreviewPersona} —
 * production middleware rejects the old `exp_has_session` fixture.
 */
export function seedPreviewExperienceSession() {
  const user = PREVIEW_USER;
  sessionStorage.removeItem("exp:auth_token");
  sessionStorage.setItem("exp:facility", JSON.stringify(PREVIEW_FACILITY));
  sessionStorage.setItem(
    "exp:shift",
    JSON.stringify({ id: "shift-preview-e2e", startedAt: new Date().toISOString(), workspace: "OPD" }),
  );
  localStorage.setItem(
    "exp:consent_accepted",
    JSON.stringify({ userId: user.id, version: "2026-04-11", acceptedAt: new Date().toISOString() }),
  );
  localStorage.setItem("exp:consent_version", "2026-04-11");
}

/** Seed non-auth client context. Does not unlock production middleware. */
export async function installPreviewSession(context: BrowserContext, _baseURL?: string) {
  await context.addInitScript(seedPreviewExperienceSession);
}

/** First login may land on the policy-consent gate — accept it like a real user. */
export async function acceptPreviewPoliciesIfGated(page: Page) {
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

export type PreviewAuthResult =
  | { ok: true }
  | { ok: false; reason: "mfa_required_action" | "login_failed"; detail: string };

async function keycloakRequiredActionVisible(page: Page): Promise<string | null> {
  const markers = [
    page.getByRole("heading", { name: /Mobile Authenticator Setup|Configure OTP|Update Password|Verify Email/i }),
    page.getByText(/Scan the QR code|one-time code provided by the application/i),
  ];
  for (const marker of markers) {
    if (await marker.first().isVisible({ timeout: 1_500 }).catch(() => false)) {
      return (await marker.first().innerText().catch(() => "required-action")).slice(0, 120);
    }
  }
  return null;
}

/**
 * Honest OIDC login through the progressive shell door → Keycloak → callback.
 * Uses the Personal intent so preview E2E does not require AAL2 step-up.
 * Returns `{ ok:false, reason:'mfa_required_action' }` when Keycloak demands
 * TOTP enrollment the automated suite cannot complete.
 */
export async function loginPreviewPersona(page: Page): Promise<PreviewAuthResult> {
  try {
    await page.goto(`${PREVIEW_ORIGIN}/auth/login`, { waitUntil: "domcontentloaded" });
    // Keep Personal & Family selected (default) to avoid workforce AAL2.
    const identifier = page.locator("#identifier-input");
    await identifier.waitFor({ state: "visible", timeout: 20_000 });
    await identifier.click();
    await identifier.fill("");
    // Controlled React input: type so onChange enables the submit button.
    await identifier.pressSequentially(PREVIEW_PERSONA.username, { delay: 15 });
    const continueBtn = page.getByRole("button", { name: /continue to secure sign-in/i });
    await continueBtn.waitFor({ state: "visible", timeout: 10_000 });
    await expectEnabled(page, continueBtn);
    await continueBtn.click();

    // Keycloak hosts password entry. Field names vary by theme/version.
    const password = page.locator("#password, input[name='password'], input[type='password']").first();
    const passwordVisible = await password
      .waitFor({ state: "visible", timeout: 45_000 })
      .then(() => true)
      .catch(() => false);
    if (!passwordVisible) {
      const required = await keycloakRequiredActionVisible(page);
      if (required) return { ok: false, reason: "mfa_required_action", detail: required };
      return { ok: false, reason: "login_failed", detail: `no password field at ${page.url()}` };
    }
    // Some themes pre-fill username from login_hint; fill if an empty username field exists.
    const username = page.locator("#username, input[name='username']").first();
    if (await username.isVisible().catch(() => false)) {
      const current = await username.inputValue().catch(() => "");
      if (!current) await username.fill(PREVIEW_PERSONA.username);
    }
    await password.fill(PREVIEW_PERSONA.password);
    await page.locator("#kc-login, button[type='submit'], input[type='submit']").first().click();

    // Either we land in-app, or Keycloak forces a required action (TOTP setup, etc.).
    const landed = await Promise.race([
      page
        .waitForURL(
          (url) =>
            !url.pathname.startsWith("/auth") &&
            !url.pathname.includes("/realms/") &&
            !url.pathname.includes("/protocol/openid-connect") &&
            !url.pathname.includes("/login-actions/"),
          { timeout: 60_000 },
        )
        .then(() => "app" as const),
      page
        .getByRole("heading", { name: /Mobile Authenticator Setup|Configure OTP/i })
        .first()
        .waitFor({ state: "visible", timeout: 60_000 })
        .then(() => "mfa" as const),
    ]).catch(async () => {
      const required = await keycloakRequiredActionVisible(page);
      return required ? ("mfa" as const) : ("timeout" as const);
    });

    if (landed === "mfa") {
      const detail = (await keycloakRequiredActionVisible(page)) || "Keycloak required action";
      return { ok: false, reason: "mfa_required_action", detail };
    }
    if (landed !== "app") {
      return { ok: false, reason: "login_failed", detail: `stuck at ${page.url()}` };
    }

    await acceptPreviewPoliciesIfGated(page);
    await page.evaluate(seedPreviewExperienceSession);
    return { ok: true };
  } catch (error) {
    const required = await keycloakRequiredActionVisible(page);
    if (required) return { ok: false, reason: "mfa_required_action", detail: required };
    const message = error instanceof Error ? error.message : String(error);
    return { ok: false, reason: "login_failed", detail: message.slice(0, 240) };
  }
}

async function expectEnabled(page: Page, locator: ReturnType<Page["getByRole"]>) {
  await locator.waitFor({ state: "attached", timeout: 10_000 });
  for (let i = 0; i < 30; i++) {
    if (await locator.isEnabled().catch(() => false)) return;
    await page.waitForTimeout(100);
  }
  if (!(await locator.isEnabled().catch(() => false))) {
    throw new Error("Continue button stayed disabled after identifier entry");
  }
}

/** Install client context + perform real login for a page under test. */
export async function authenticatePreviewPage(page: Page): Promise<PreviewAuthResult> {
  await installPreviewSession(page.context());
  return loginPreviewPersona(page);
}

/** Skip the current test when live preview cannot complete opaque-session login. */
export function skipUnlessAuthenticated(result: PreviewAuthResult): asserts result is { ok: true } {
  if (result.ok) return;
  if (result.reason === "mfa_required_action") {
    test.skip(true, `Preview persona requires Keycloak MFA enrollment: ${result.detail}`);
  }
  test.skip(true, `Preview OIDC login failed: ${result.detail}`);
}

/** Navigate to a domain route (session seeded via addInitScript on each document). */
export async function gotoAppPath(page: import("@playwright/test").Page, path: string) {
  await page.goto(path, { waitUntil: "domcontentloaded" });
}

/** POST to same-origin BFF path; auth is the opaque session cookie + CSRF double-submit. */
export async function bffPostFromBrowser(
  page: Page,
  path: string,
  body: Record<string, unknown>,
) {
  return page.evaluate(
    async ({ path, body, tenantId }) => {
      const facility = JSON.parse(sessionStorage.getItem("exp:facility") || "{}");
      const requestId =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
        "X-Tenant-ID": tenantId,
        "X-Pod-ID": "default",
        "X-Request-ID": requestId,
        "X-Correlation-ID": requestId,
        "Idempotency-Key": requestId,
        "X-Actor-Type": "PROVIDER",
        "X-Purpose-Of-Use": "TREATMENT",
        "X-Device-Fingerprint": "preview-e2e",
      };
      const csrf = document.cookie
        .split("; ")
        .find((row) => row.startsWith("__Host-impilo_csrf="))
        ?.split("=")
        .slice(1)
        .join("=");
      if (csrf) headers["X-CSRF-Token"] = decodeURIComponent(csrf);
      if (facility.id) headers["X-Facility-ID"] = facility.id;
      const res = await fetch(path, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
        credentials: "same-origin",
      });
      const text = await res.text();
      let json: unknown = null;
      try {
        json = JSON.parse(text);
      } catch {
        json = text;
      }
      return { ok: res.ok, status: res.status, json };
    },
    { path, body, tenantId: PREVIEW_TENANT_ID },
  );
}

/** GET from same-origin BFF; auth is the opaque session cookie. */
export async function bffGetFromBrowser(page: Page, path: string) {
  return page.evaluate(
    async ({ path, tenantId }) => {
      const facility = JSON.parse(sessionStorage.getItem("exp:facility") || "{}");
      const requestId =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const headers: Record<string, string> = {
        "X-Tenant-ID": tenantId,
        "X-Pod-ID": "default",
        "X-Request-ID": requestId,
        "X-Correlation-ID": requestId,
        "X-Actor-Type": "PROVIDER",
        "X-Purpose-Of-Use": "TREATMENT",
      };
      if (facility.id) headers["X-Facility-ID"] = facility.id;
      const res = await fetch(path, { headers, credentials: "same-origin" });
      const text = await res.text();
      return { ok: res.ok, status: res.status, text };
    },
    { path, tenantId: PREVIEW_TENANT_ID },
  );
}

export function uniqueMarker(prefix: string) {
  return `${prefix}-${Date.now()}`;
}

export async function isPreviewLoginScreen(page: import("@playwright/test").Page) {
  const markers = [
    page.getByText(/Sign in to continue to Impilo/i),
    page.getByRole("heading", { name: /^Welcome back$/i }),
    page.getByRole("textbox", { name: /Email or phone/i }),
  ];
  for (const marker of markers) {
    if (await marker.isVisible({ timeout: 2_000 }).catch(() => false)) {
      return true;
    }
  }
  return false;
}

/** Wait for a BFF POST to succeed (2xx). Returns null if none observed. */
export async function waitForBffPost(
  page: import("@playwright/test").Page,
  pathFragment: string,
  timeoutMs = 30_000,
) {
  return page
    .waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().includes(pathFragment) &&
        response.status() >= 200 &&
        response.status() < 300,
      { timeout: timeoutMs },
    )
    .catch(() => null);
}

/** Wait for a BFF GET to succeed after reload. */
export async function waitForBffGet(
  page: import("@playwright/test").Page,
  pathFragment: string,
  timeoutMs = 30_000,
) {
  return page
    .waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        response.url().includes(pathFragment) &&
        response.status() >= 200 &&
        response.status() < 300,
      { timeout: timeoutMs },
    )
    .catch(() => null);
}
