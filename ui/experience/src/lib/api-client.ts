/**
 * Experience UI — API Client with v1.1 Header Injection
 *
 * Every outbound request to the Experience-BFF carries the mandatory v1.1 headers:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
 *
 * Command requests (POST/PUT/PATCH) also carry Idempotency-Key.
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

function getAuthUser(): { id: string; actorType: string } | null {
  if (typeof window !== "undefined") {
    const raw = sessionStorage.getItem("exp:auth_user");
    if (raw) {
      try { return JSON.parse(raw); } catch { /* ignore */ }
    }
  }
  return null;
}

function getV11Headers(): Record<string, string> {
  const user = getAuthUser();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Tenant-ID": getTenantId(),
    "X-Pod-ID": getPodId(),
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": getCorrelationId(),
    "X-Purpose-Of-Use": "DIRECT_CARE",
  };

  if (user?.id) headers["X-Actor-ID"] = user.id;
  if (user?.actorType) headers["X-Actor-Type"] = user.actorType;

  const token = getAuthToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return headers;
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
