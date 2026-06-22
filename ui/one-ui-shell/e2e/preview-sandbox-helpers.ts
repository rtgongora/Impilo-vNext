import type { BrowserContext } from "@playwright/test";

export const PREVIEW_ORIGIN = process.env.PLAYWRIGHT_BASE_URL ?? "http://41.57.127.235";

export const RUN_PREVIEW =
  process.env.PREVIEW_SANDBOX_E2E === "1" || /41\.57\.127\.235|127\.0\.0\.1|localhost/.test(PREVIEW_ORIGIN);

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
  id: "FAC-HARARE-CENTRAL",
  name: "Harare Central Hospital",
  code: "HCH-001",
  facilityType: "HOSPITAL",
  capabilities: ["INPATIENT", "OUTPATIENT", "EMERGENCY", "PHARMACY", "LAB"],
};

export function seedPreviewExperienceSession() {
  const user = PREVIEW_USER;
  sessionStorage.setItem("exp:auth_token", "preview-persist-e2e-token");
  sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
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

export async function installPreviewSession(context: BrowserContext, baseURL?: string) {
  const origin = baseURL ?? PREVIEW_ORIGIN;
  await context.addCookies([{ name: "exp_has_session", value: "1", url: origin }]);
  await context.addInitScript(seedPreviewExperienceSession);
}

/** Navigate to a domain route (session seeded via addInitScript on each document). */
export async function gotoAppPath(page: import("@playwright/test").Page, path: string) {
  await page.goto(path, { waitUntil: "domcontentloaded" });
}

/** POST to same-origin BFF path with trust headers derived from the seeded preview session. */
export async function bffPostFromBrowser(
  page: import("@playwright/test").Page,
  path: string,
  body: Record<string, unknown>,
) {
  return page.evaluate(
    async ({ path, body }) => {
      const user = JSON.parse(sessionStorage.getItem("exp:auth_user") || "{}");
      const facility = JSON.parse(sessionStorage.getItem("exp:facility") || "{}");
      const requestId =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
        "X-Tenant-ID": "default",
        "X-Pod-ID": "default",
        "X-Request-ID": requestId,
        "X-Correlation-ID": requestId,
        "X-Actor-ID": user.id || "preview-persist-e2e",
        "X-Actor-Type": user.actorType || "PROVIDER",
        "X-Purpose-Of-Use": "TREATMENT",
        "X-Device-Fingerprint": "preview-e2e",
      };
      if (user.providerId) headers["X-Provider-ID"] = user.providerId;
      if (facility.id) headers["X-Facility-ID"] = facility.id;
      const res = await fetch(path, { method: "POST", headers, body: JSON.stringify(body) });
      const text = await res.text();
      let json: unknown = null;
      try {
        json = JSON.parse(text);
      } catch {
        json = text;
      }
      return { ok: res.ok, status: res.status, json };
    },
    { path, body },
  );
}

/** GET from same-origin BFF with trust headers. */
export async function bffGetFromBrowser(page: import("@playwright/test").Page, path: string) {
  return page.evaluate(async (path) => {
    const user = JSON.parse(sessionStorage.getItem("exp:auth_user") || "{}");
    const facility = JSON.parse(sessionStorage.getItem("exp:facility") || "{}");
    const requestId =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const headers: Record<string, string> = {
      "X-Tenant-ID": "default",
      "X-Pod-ID": "default",
      "X-Request-ID": requestId,
      "X-Correlation-ID": requestId,
      "X-Actor-ID": user.id || "preview-persist-e2e",
      "X-Actor-Type": user.actorType || "PROVIDER",
      "X-Purpose-Of-Use": "TREATMENT",
    };
    if (user.providerId) headers["X-Provider-ID"] = user.providerId;
    if (facility.id) headers["X-Facility-ID"] = facility.id;
    const res = await fetch(path, { headers });
    const text = await res.text();
    return { ok: res.ok, status: res.status, text };
  }, path);
}

export function uniqueMarker(prefix: string) {
  return `${prefix}-${Date.now()}`;
}

export async function isPreviewLoginScreen(page: import("@playwright/test").Page) {
  return page
    .getByText(/Sign in to continue to Impilo/i)
    .isVisible({ timeout: 5_000 })
    .catch(() => false);
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
