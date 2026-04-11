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
 * endpoint before failing. If refresh succeeds, retries the original request
 * with the new token. If refresh fails, clears auth and redirects to login.
 */

import { useAuthStore } from "@/hooks/useAuthStore";

const BFF_BASE_URL = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160";

export interface ApiResponse<T> {
  data: T;
  meta?: {
    request_id: string;
    correlation_id: string;
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
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": getCorrelationId(),
  };

  const token = getAuthToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // ── Actor identity (Health OS §5–§6: who is acting) ──────────
  const authUser = getStoredAuthUser();
  if (authUser?.id) {
    headers["X-Actor-ID"] = authUser.id;           // Health ID — person anchor
  }
  if (authUser?.actorType) {
    headers["X-Actor-Type"] = authUser.actorType;
  }

  // Provider ID — activated regulated professional role (sign in as person, practice as provider)
  const providerId = getContextString("exp:provider_id");
  if (providerId) {
    headers["X-Provider-ID"] = providerId;
  }

  // ── Governance (Health OS §11: why / under what authority) ────
  headers["X-Purpose-Of-Use"] = getPurposeOfUse();

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

function getStoredAuthUser(): { id?: string; actorType?: string } | null {
  return getStoredJson<{ id?: string; actorType?: string }>("exp:auth_user");
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
  // Canonical dev default — matches V2/V21+ seeds. Golden-path integration tests use X-Tenant-ID "moh-zw".
  return "tenant-moh-zw";
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
  return crypto.randomUUID();
}

function getAuthToken(): string | null {
  if (typeof window !== "undefined") {
    return sessionStorage.getItem("exp:auth_token");
  }
  return null;
}

function getRefreshToken(): string | null {
  if (typeof window !== "undefined") {
    return sessionStorage.getItem("exp:refresh_token");
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
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  // If a refresh is already in progress, wait for it
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const headers = getV11Headers();
      headers["Idempotency-Key"] = crypto.randomUUID();
      // Don't send the expired token for the refresh call
      delete headers["Authorization"];

      const response = await fetch(`${BFF_BASE_URL}/internal/v1/auth/refresh`, {
        method: "POST",
        headers,
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) return false;

      const data = await response.json();
      const attrs = data?.data?.attributes;
      if (!attrs?.token) return false;

      if (attrs.user) {
        useAuthStore
          .getState()
          .setAuth(attrs.user, attrs.token, attrs.refreshToken ?? refreshToken, attrs.expiresAt ?? null);
      } else {
        useAuthStore.getState().setTokens(attrs.token, attrs.refreshToken ?? refreshToken, attrs.expiresAt ?? null);
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
function handleAuthFailure(): void {
  if (typeof window !== "undefined") {
    useAuthStore.getState().clearAuth();

    // Only redirect if not already on auth page
    if (!window.location.pathname.startsWith("/auth")) {
      window.location.href = "/auth/login";
    }
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  responseType: "json" | "text" = "json",
): Promise<T> {
  const headers = getV11Headers();

  if (["POST", "PUT", "PATCH"].includes(method)) {
    headers["Idempotency-Key"] = crypto.randomUUID();
  }

  const response = await fetch(`${BFF_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401 && !path.includes("/auth/")) {
    // Attempt token refresh
    const refreshed = await attemptRefresh();
    if (refreshed) {
      // Retry the original request with the new token
      const retryHeaders = getV11Headers();
      if (["POST", "PUT", "PATCH"].includes(method)) {
        retryHeaders["Idempotency-Key"] = crypto.randomUUID();
      }
      const retryResponse = await fetch(`${BFF_BASE_URL}${path}`, {
        method,
        headers: retryHeaders,
        body: body ? JSON.stringify(body) : undefined,
      });

      if (retryResponse.ok) {
        if (responseType === "text") {
          return retryResponse.text() as Promise<T>;
        }
        return retryResponse.json();
      }

      if (retryResponse.status === 401) {
        handleAuthFailure();
      }

      const errorBody = await retryResponse.json().catch(() => null);
      throw { status: retryResponse.status, ...(errorBody || {}) };
    }

    // Refresh failed — clear auth and redirect
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

  if (responseType === "text") {
    return response.text() as Promise<T>;
  }

  return response.json();
}

export const apiClient = {
  get: <T>(path: string) => request<T>("GET", path),
  getText: (path: string) => request<string>("GET", path, undefined, "text"),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
