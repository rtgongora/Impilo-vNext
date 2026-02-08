/**
 * MUSHEX Finance Console — Trust-Aware API Client
 *
 * Every outbound request injects the mandatory trust headers from the
 * session store. This ensures that Envoy ext_authz -> TSHEPO
 * can evaluate every request before it reaches the MUSHEX backend.
 */

import { useSessionStore } from "@/stores/sessionStore";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:10000";

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const correlationId = crypto.randomUUID();

  const state = useSessionStore.getState();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Correlation-Id": correlationId,
    "X-Purpose-Of-Use": state.purposeOfUse || "FINANCE",
    "X-Device-Fingerprint": "mushex-finance-console",
  };

  if (state.accessToken) {
    headers["Authorization"] = `Bearer ${state.accessToken}`;
  }

  if (state.tenantId) {
    headers["X-Tenant-Id"] = state.tenantId;
  }

  if (state.actorId) {
    headers["X-Actor-Id"] = state.actorId;
  }

  if (state.actorType) {
    headers["X-Actor-Type"] = state.actorType;
  }

  if (state.facilityId) {
    headers["X-Facility-Id"] = state.facilityId;
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const envelope = await res.json();

  if (!envelope.success) {
    throw new Error(envelope.error?.message ?? `API error (${res.status})`);
  }

  return envelope.data as T;
}

export const apiClient = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
