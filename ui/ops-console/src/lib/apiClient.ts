/**
 * Ops Console — Trust-Aware API Client
 *
 * Every outbound request injects the mandatory trust headers from the
 * operator's session store. This ensures that Envoy ext_authz → TSHEPO
 * can evaluate every request before it reaches a backend service.
 *
 * Header contract must match: shared-ui/lib/contracts.ts ↔ TrustHeaders.java
 */

import { TRUST_HEADERS, type ApiEnvelope } from "shared-ui";
import { useOpsSession } from "@/stores/sessionStore";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || "";

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const correlationId = crypto.randomUUID();

  const { session, workContext, purposeOfUse } = useOpsSession.getState();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    [TRUST_HEADERS.CORRELATION_ID]: correlationId,
    [TRUST_HEADERS.PURPOSE_OF_USE]: purposeOfUse,
  };

  if (session) {
    headers["Authorization"] = `Bearer ${session.accessToken}`;
    headers[TRUST_HEADERS.ACTOR_ID] = session.actorId;
    headers[TRUST_HEADERS.ACTOR_TYPE] = session.actorType;
  }

  if (workContext) {
    headers[TRUST_HEADERS.TENANT_ID] = workContext.tenantId;
    if (workContext.facilityId) {
      headers[TRUST_HEADERS.FACILITY_ID] = workContext.facilityId;
    }
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const envelope: ApiEnvelope<T> = await res.json();

  if (!envelope.success) {
    throw new Error(envelope.error?.message ?? `API error (${res.status})`);
  }

  return envelope.data;
}

export const apiClient = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
