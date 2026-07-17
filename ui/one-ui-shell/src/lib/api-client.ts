/**
 * Experience UI — API Client with v1.2 Header Injection and Token Refresh
 *
 * Aligned with Health OS Manifest v1.2 (see docs/doctrine/health-os-doctrine.md).
 *
 * Every outbound request carries mandatory v1.2 headers:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
 *
 * Actor identity headers (who):
 *   X-Actor-ID (Health ID — person anchor), X-Actor-Type, X-Provider-ID (regulated role)
 *
 * Context headers (where):
 *   X-Facility-ID, X-Department-ID, X-Ward-ID, X-Workspace-ID, X-Programme-ID, X-Shift-ID
 *
 * Governance headers (why):
 *   X-Purpose-Of-Use, X-Assurance-Level, X-Access-Mode
 *
 * Command requests (POST/PUT/PATCH) also carry Idempotency-Key.
 *
 * On 401 responses, attempts a single token refresh via the BFF /auth/refresh
 * endpoint using the HttpOnly refresh cookie before failing. If refresh succeeds, retries the original request
 * with the new token. If refresh fails, clears auth and redirects to login.
 */

import { useAuthStore } from "@/hooks/useAuthStore";
import { randomUUID } from "@/lib/uuid";
import { isStepUpRequired } from "@/lib/stepUp";
import { recordNompiloFailure } from "@/lib/nompilo-failure";

// Use NEXT_PUBLIC_BFF_URL when explicitly set (Docker, tests, SSR).
// In the browser without an explicit URL, use relative paths so requests proxy
// through the Next.js dev server (see next.config.mjs rewrites), avoiding CORS.
const BFF_BASE_URL = process.env.NEXT_PUBLIC_BFF_URL || (typeof window !== "undefined" ? "" : "http://localhost:8160");

// When BFF_BASE_URL is a full URL (non-empty), requests are cross-origin.
// Use "include" to send HttpOnly cookies (refresh token) cross-origin.
// Use "same-origin" for relative paths so the browser's same-origin cookie
// policy is enforced in production where the BFF is behind the same reverse proxy.
const fetchCredentials: RequestCredentials = BFF_BASE_URL ? "include" : "same-origin";

export interface ApiResponse<T> {
  data: T;
  meta?: {
    request_id: string;
    correlation_id: string;
    journey_id?: string;
    core_transaction_id?: string;
    encounter_id?: string;
    page?: {
      number: number;
      size: number;
      total_elements: number;
      total_pages: number;
    };
  };
}

export interface ApiError {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
    request_id: string;
    correlation_id: string;
  };
}

// Refresh state to prevent concurrent refresh attempts
let refreshPromise: Promise<boolean> | null = null;

function getV12Headers(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Tenant-ID": getTenantId(),
    "X-Pod-ID": getPodId(),
    "X-Request-ID": randomUUID(),
    "X-Correlation-ID": getCorrelationId(),
  };

  const token = getAuthToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // ── Actor identity (Health OS §5–§6: who is acting) ──────────
  const authUser = getStoredAuthUser();
  const actorAnchor = authUser?.healthId?.trim() || authUser?.id;
  if (actorAnchor) {
    headers["X-Actor-ID"] = actorAnchor;           // Health ID — person anchor
  }
  if (authUser?.email) {
    headers["X-Actor-Email"] = authUser.email;
  }
  if (authUser?.actorType) {
    headers["X-Actor-Type"] = authUser.actorType;
  }

  const loginMethod = authUser?.loginMethod;
  if (loginMethod) {
    headers["X-Login-Method"] = loginMethod;
  }

  // Provider ID — activated regulated professional role (sign in as person, practice as provider)
  const providerId = getContextString("exp:provider_id");
  if (providerId) {
    headers["X-Provider-ID"] = providerId;
  }

  // Subject — delegated "acting for" context (L5). Set only when the user has explicitly entered a
  // delegation context; the trust plane (PolicyEngine Step 4.5) authorises it against an active
  // Mvumo delegation. Never sent for normal self-access.
  const actingForSubject = getContextString("exp:acting_for_subject");
  if (actingForSubject) {
    headers["X-Subject-ID"] = actingForSubject;
  }

  // ── Governance (Health OS §11: why / under what authority) ────
  headers["X-Purpose-Of-Use"] = getPurposeOfUse();
  headers["X-Device-Fingerprint"] =
    getContextString("exp:device_fingerprint") || "experience-web";

  const accessMode = getContextString("exp:access_mode");
  if (accessMode) {
    headers["X-Access-Mode"] = accessMode;
  }

  const assuranceLevel = getContextString("exp:assurance_level");
  if (assuranceLevel) {
    headers["X-Assurance-Level"] = assuranceLevel;
  }

  // ── Operational context (Health OS §7: where / under what) ────
  const facilityId = getContextId("exp:facility");
  if (facilityId) {
    headers["X-Facility-ID"] = facilityId;
  }

  const departmentId = getContextString("exp:department_id");
  if (departmentId) {
    headers["X-Department-ID"] = departmentId;
  }

  const wardId = getContextString("exp:ward_id");
  if (wardId) {
    headers["X-Ward-ID"] = wardId;
  }

  const workspaceId = getContextId("exp:workspace");
  if (workspaceId) {
    headers["X-Workspace-ID"] = workspaceId;
  }

  const programmeId = getContextString("exp:programme_id");
  if (programmeId) {
    headers["X-Programme-ID"] = programmeId;
  }

  const shiftId = getContextId("exp:shift");
  if (shiftId) {
    headers["X-Shift-ID"] = shiftId;
  }

  return headers;
}

/** @deprecated Use getV12Headers — kept as alias during migration */
const getV11Headers = getV12Headers;

function getStoredJson<T>(key: string): T | null {
  if (typeof window === "undefined") return null;

  const raw = sessionStorage.getItem(key);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function getStoredAuthUser(): {
  id?: string;
  healthId?: string;
  email?: string;
  actorType?: string;
  loginMethod?: string;
} | null {
  const liveUser = useAuthStore.getState().user;
  if (liveUser) {
    return {
      id: liveUser.id,
      healthId: liveUser.healthId,
      email: liveUser.email,
      actorType: liveUser.actorType,
      loginMethod: liveUser.loginMethod,
    };
  }
  return getStoredJson<{
    id?: string;
    healthId?: string;
    email?: string;
    actorType?: string;
    loginMethod?: string;
  }>("exp:auth_user");
}

function getContextId(key: string): string | null {
  const context = getStoredJson<{ id?: string }>(key);
  return context?.id ?? null;
}

/** Read a plain string from sessionStorage (for flat context values like provider_id). */
function getContextString(key: string): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem(key) || null;
}

function getTenantId(): string {
  if (typeof window !== "undefined") {
    const stored = sessionStorage.getItem("exp:tenant_id");
    if (stored) return stored;
  }
  // Canonical dev default UUID — matches V2/V21+ seed: Tenant moh-zw = 00000000-0000-4000-8000-000000000001
  return "00000000-0000-4000-8000-000000000001";
}

function getPodId(): string {
  if (typeof window !== "undefined") {
    const stored = sessionStorage.getItem("exp:pod_id");
    if (stored) return stored;
  }
  return "national-spine";
}

function getCorrelationId(): string {
  if (typeof window !== "undefined") {
    const stored = sessionStorage.getItem("exp:correlation_id");
    if (stored) return stored;
  }
  return randomUUID();
}

function getAuthToken(): string | null {
  const liveToken = useAuthStore.getState().token;
  if (liveToken) {
    return liveToken;
  }
  if (typeof window !== "undefined") {
    return sessionStorage.getItem("exp:auth_token");
  }
  return null;
}

function getPurposeOfUse(): string {
  if (typeof window !== "undefined") {
    const stored = sessionStorage.getItem("exp:purpose_of_use");
    if (stored) return stored;

    const workMode = sessionStorage.getItem("exp:work_mode");
    switch (workMode) {
      case "finance":
        return "PAYMENT";
      case "admin":
      case "oversight":
        return "OPERATIONS";
      case "community_outreach":
        return "PUBLIC_HEALTH";
      case "emergency_response":
        return "EMERGENCY";
      default:
        return "TREATMENT";
    }
  }

  return "TREATMENT";
}

/**
 * Attempt to refresh the session token using the refresh_token.
 * Returns true if refresh succeeded, false otherwise.
 * Uses a singleton promise to prevent concurrent refresh attempts.
 */
async function attemptRefresh(): Promise<boolean> {
  if (typeof window === "undefined") return false;
  // When same-origin, the sentinel cookie is readable; skip refresh if absent.
  // When cross-origin (BFF_BASE_URL set), document.cookie belongs to the UI
  // origin and won't include cookies set by the BFF — skip the check to ensure
  // the refresh attempt always reaches the server.
  if (!BFF_BASE_URL) {
    const hasSessionCookie = document.cookie.includes("exp_has_session=1");
    if (!hasSessionCookie) return false;
  }

  // If a refresh is already in progress, wait for it
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const headers = getV11Headers();
      headers["Idempotency-Key"] = randomUUID();
      // Don't send the expired token for the refresh call
      delete headers["Authorization"];

      const response = await fetch(`${BFF_BASE_URL}/internal/v1/auth/refresh`, {
        method: "POST",
        headers,
        credentials: fetchCredentials,
      });

      if (!response.ok) return false;

      const data = await response.json();
      const attrs = data?.data?.attributes;
      if (!attrs?.token) return false;

      if (attrs.user) {
        // Preserve person-first identity fields from the current session
        const currentUser = useAuthStore.getState().user;
        const mergedUser = {
          ...attrs.user,
          assuranceLevel: attrs.user.assuranceLevel ?? currentUser?.assuranceLevel ?? "VERIFIED",
          providerActivated: attrs.user.providerActivated ?? currentUser?.providerActivated ?? false,
          linkedIds: attrs.user.linkedIds ?? currentUser?.linkedIds,
          healthId: attrs.user.healthId ?? currentUser?.healthId,
          providerId: currentUser?.providerId,
        };
        useAuthStore
          .getState()
          .setAuth(mergedUser, attrs.token, null, attrs.expiresAt ?? null);
      } else {
        useAuthStore.getState().setTokens(attrs.token, null, attrs.expiresAt ?? null);
      }

      return true;
    } catch {
      return false;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

/**
 * Clear auth state and redirect to login.
 */
const AUTH_BYPASS_PREFIXES = ["/auth", "/consent", "/privacy", "/terms", "/share"];

function handleAuthFailure(): void {
  if (typeof window !== "undefined") {
    const isOnBypassPath = AUTH_BYPASS_PREFIXES.some((p) =>
      window.location.pathname.startsWith(p)
    );
    if (isOnBypassPath) return;

    const store = useAuthStore.getState();
    if (!store.isTokenExpired()) return;

    store.clearAuth();
    window.location.href = "/auth/login";
  }
}

/**
 * Feed a failed API call into the Nompilo failure signal so the assistant can offer
 * context-aware recovery. Privacy-safe by construction: only the page route, the machine
 * error code, and the correlation/request ids (already on the wire) are captured — never
 * the request body, response body, or any PII/clinical/credential content.
 *
 * 401s that are session-expiry/step-up are excluded (they have their own refresh/challenge
 * flow and are not user-actionable failures).
 */
function recordApiFailure(
  status: number,
  errorBody: unknown,
  response?: Response,
): void {
  if (typeof window === "undefined") return;
  const envelope = (errorBody as Partial<ApiError> | null)?.error;
  const code = envelope?.code || `HTTP_${status}`;
  const correlationId =
    envelope?.correlation_id || response?.headers?.get("X-Correlation-ID") || undefined;
  const requestId =
    envelope?.request_id || response?.headers?.get("X-Request-ID") || undefined;
  recordNompiloFailure({
    route: window.location.pathname,
    code,
    correlationId,
    requestId,
    status,
  });
}

export type ApiRequestOptions = {
  extraHeaders?: Record<string, string>;
};

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  responseType: "json" | "text" = "json",
  opts?: ApiRequestOptions,
): Promise<T> {
  const headers = { ...getV11Headers(), ...opts?.extraHeaders };

  if (["POST", "PUT", "PATCH"].includes(method) || (method === "DELETE" && body !== undefined)) {
    // Respect a caller-provided key (e.g. one key per donation attempt) — only auto-generate.
    headers["Idempotency-Key"] = headers["Idempotency-Key"] ?? randomUUID();
  }

  const response = await fetch(`${BFF_BASE_URL}${path}`, {
    method,
    headers,
    credentials: fetchCredentials,
    body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401 && !path.includes("/auth/")) {
    // A STEP_UP_REQUIRED challenge is also a 401, but it is NOT a session expiry — it must
    // surface to the caller (which shows a verification prompt), not trigger refresh/redirect.
    const unauthorizedBody = await response.json().catch(() => null);
    if (isStepUpRequired(unauthorizedBody)) {
      throw { status: 401, ...(unauthorizedBody || {}) };
    }

    // Attempt token refresh
    const refreshed = await attemptRefresh();
    if (refreshed) {
      // Retry the original request with the new token
      const retryHeaders = { ...getV11Headers(), ...opts?.extraHeaders };
      if (["POST", "PUT", "PATCH"].includes(method) || (method === "DELETE" && body !== undefined)) {
        // Same logical attempt after token refresh — reuse a caller-provided key.
        retryHeaders["Idempotency-Key"] = retryHeaders["Idempotency-Key"] ?? randomUUID();
      }
      const retryResponse = await fetch(`${BFF_BASE_URL}${path}`, {
        method,
        headers: retryHeaders,
        credentials: fetchCredentials,
        body: body !== undefined && body !== null ? JSON.stringify(body) : undefined,
      });

      if (retryResponse.ok) {
        if (responseType === "text") {
          return retryResponse.text() as Promise<T>;
        }
        return retryResponse.json();
      }

      const retryBody = await retryResponse.json().catch(() => null);
      if (retryResponse.status === 401 && !isStepUpRequired(retryBody)) {
        handleAuthFailure();
      }

      recordApiFailure(retryResponse.status, retryBody, retryResponse);
      throw { status: retryResponse.status, ...(retryBody || {}) };
    }

    // Refresh failed — clear auth and redirect
    handleAuthFailure();
    throw { status: 401, error: { code: "SESSION_EXPIRED", message: "Session expired" } };
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    recordApiFailure(response.status, errorBody, response);
    throw {
      status: response.status,
      ...(errorBody || {}),
    };
  }

  if (responseType === "text") {
    return response.text() as Promise<T>;
  }

  return response.json();
}

async function requestForm<T>(
  method: string,
  path: string,
  body: FormData,
  opts?: ApiRequestOptions,
): Promise<T> {
  const headers = { ...getV11Headers(), ...opts?.extraHeaders };
  delete headers["Content-Type"];

  if (["POST", "PUT", "PATCH"].includes(method)) {
    headers["Idempotency-Key"] = randomUUID();
  }

  const response = await fetch(`${BFF_BASE_URL}${path}`, {
    method,
    headers,
    credentials: fetchCredentials,
    body,
  });

  if (response.status === 401 && !path.includes("/auth/")) {
    const refreshed = await attemptRefresh();
    if (refreshed) {
      const retryHeaders = { ...getV11Headers(), ...opts?.extraHeaders };
      delete retryHeaders["Content-Type"];
      if (["POST", "PUT", "PATCH"].includes(method)) {
        retryHeaders["Idempotency-Key"] = randomUUID();
      }

      const retryResponse = await fetch(`${BFF_BASE_URL}${path}`, {
        method,
        headers: retryHeaders,
        credentials: fetchCredentials,
        body,
      });

      if (retryResponse.ok) {
        return retryResponse.json();
      }

      if (retryResponse.status === 401) {
        handleAuthFailure();
      }

      const errorBody = await retryResponse.json().catch(() => null);
      throw { status: retryResponse.status, ...(errorBody || {}) };
    }

    handleAuthFailure();
    throw { status: 401, error: { code: "SESSION_EXPIRED", message: "Session expired" } };
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw {
      status: response.status,
      ...(errorBody || {}),
    };
  }

  return response.json();
}

/** Authenticated GET returning a binary body (e.g. DICOM rendered frames). */
async function requestBlob(path: string): Promise<Blob> {
  const headers = { ...getV11Headers() };
  const response = await fetch(`${BFF_BASE_URL}${path}`, {
    method: "GET",
    headers,
    credentials: fetchCredentials,
  });

  if (response.status === 401) {
    const refreshed = await attemptRefresh();
    if (refreshed) {
      const retryHeaders = { ...getV11Headers() };
      const retryResponse = await fetch(`${BFF_BASE_URL}${path}`, {
        method: "GET",
        headers: retryHeaders,
        credentials: fetchCredentials,
      });
      if (retryResponse.ok) {
        return retryResponse.blob();
      }
      if (retryResponse.status === 401) {
        handleAuthFailure();
      }
    } else {
      handleAuthFailure();
    }
    throw { status: 401, error: { code: "SESSION_EXPIRED", message: "Session expired" } };
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw {
      status: response.status,
      ...(errorBody || {}),
    };
  }

  return response.blob();
}

export const apiClient = {
  get: <T>(path: string, opts?: ApiRequestOptions) => request<T>("GET", path, undefined, "json", opts),
  getBlob: (path: string) => requestBlob(path),
  getText: (path: string, opts?: ApiRequestOptions) => request<string>("GET", path, undefined, "text", opts),
  post: <T>(path: string, body?: unknown, opts?: ApiRequestOptions) => request<T>("POST", path, body, "json", opts),
  put: <T>(path: string, body?: unknown, opts?: ApiRequestOptions) => request<T>("PUT", path, body, "json", opts),
  patch: <T>(path: string, body?: unknown, opts?: ApiRequestOptions) => request<T>("PATCH", path, body, "json", opts),
  delete: <T>(path: string, body?: unknown, opts?: ApiRequestOptions) => request<T>("DELETE", path, body, "json", opts),
  postForm: <T>(path: string, body: FormData, opts?: ApiRequestOptions) => requestForm<T>("POST", path, body, opts),
};
