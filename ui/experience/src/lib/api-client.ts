/**
 * Experience UI — API Client with v1.1 Header Injection and Token Refresh
 *
 * Every outbound request to the Experience-BFF carries the mandatory v1.1 headers:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
 *
 * Command requests (POST/PUT/PATCH) also carry Idempotency-Key.
 *
 * On 401 responses, attempts a single token refresh via the BFF /auth/refresh
 * endpoint before failing. If refresh succeeds, retries the original request
 * with the new token. If refresh fails, clears auth and redirects to login.
 */

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

function getV11Headers(): Record<string, string> {
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

  const authUser = getStoredAuthUser();
  if (authUser?.id) {
    headers["X-Actor-ID"] = authUser.id;
  }
  if (authUser?.actorType) {
    headers["X-Actor-Type"] = authUser.actorType;
  }

  headers["X-Purpose-Of-Use"] = getPurposeOfUse();

  const facilityId = getContextId("exp:facility");
  if (facilityId) {
    headers["X-Facility-ID"] = facilityId;
  }

  const workspaceId = getContextId("exp:workspace");
  if (workspaceId) {
    headers["X-Workspace-ID"] = workspaceId;
  }

  const shiftId = getContextId("exp:shift");
  if (shiftId) {
    headers["X-Shift-ID"] = shiftId;
  }

  return headers;
}

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

function getTenantId(): string {
  if (typeof window !== "undefined") {
    const stored = sessionStorage.getItem("exp:tenant_id");
    if (stored) return stored;
  }
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

      // Update session storage with new tokens
      sessionStorage.setItem("exp:auth_token", attrs.token);
      if (attrs.refreshToken) sessionStorage.setItem("exp:refresh_token", attrs.refreshToken);
      if (attrs.expiresAt) sessionStorage.setItem("exp:expires_at", attrs.expiresAt);
      if (attrs.user) sessionStorage.setItem("exp:auth_user", JSON.stringify(attrs.user));

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
    sessionStorage.removeItem("exp:auth_token");
    sessionStorage.removeItem("exp:auth_user");
    sessionStorage.removeItem("exp:refresh_token");
    sessionStorage.removeItem("exp:expires_at");

    // Only redirect if not already on auth page
    if (!window.location.pathname.startsWith("/auth")) {
      window.location.href = "/auth/login";
    }
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown
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

  return response.json();
}

export const apiClient = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
