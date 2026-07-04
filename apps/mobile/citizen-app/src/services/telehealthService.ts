/**
 * Telehealth Service — Teleconsult requests and session management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/telehealth/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { TelehealthSession } from "../types";

const V1 = "/internal/v1/mobile/citizen/telehealth";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchSessions(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: TelehealthSession[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<TelehealthSession>>(`${V1}/sessions${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchSession(id: string): Promise<TelehealthSession> {
  const response = await apiClient.get<{ data: TelehealthSession }>(
    `${V1}/sessions/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function requestTeleconsult(params: {
  reason: string;
  preferredDate?: string;
  sessionType?: string;
  providerId?: string;
  purposeOfUse?: "TREATMENT" | "EMERGENCY" | "OPERATIONS" | "PUBLIC_HEALTH" | "PAYMENT";
  consentReference?: string;
}): Promise<TelehealthSession> {
  const purposeOfUse = params.purposeOfUse ?? "TREATMENT";
  const payload = {
    ...params,
    sessionProvider:
      params.sessionType === "VIDEO" || params.sessionType === "AUDIO"
        ? "EXTERNAL_MANAGED"
        : undefined,
  };
  const response = await apiClient.post<{ data: TelehealthSession }>(
    `${V1}/sessions`,
    payload,
    { purposeOfUse }
  );
  return response.data.data;
}

export async function joinSession(
  id: string
): Promise<{ session: TelehealthSession; token: string; channel: string }> {
  const response = await apiClient.post<{
    data: (TelehealthSession & { token?: string; accessToken?: string; channel?: string; attributes?: TelehealthSession & { token?: string; accessToken?: string; channel?: string } });
  }>(`${V1}/sessions/${encodeURIComponent(id)}/join`);
  const row = response.data.data;
  const attrs = row.attributes ?? row;
  const token = attrs.token ?? attrs.accessToken ?? attrs.sessionToken ?? "";
  const channel = attrs.channel ?? "";
  return {
    session: {
      ...attrs,
      sessionToken: token,
      channel,
      roomUrl: attrs.roomUrl ?? row.roomUrl,
    },
    token,
    channel,
  };
}

export async function endSession(id: string, notes?: string): Promise<void> {
  await apiClient.post(`${V1}/sessions/${encodeURIComponent(id)}/end`, { notes });
}

/* ── Governed RTC media token (shared teleconsult contract) ──
 *
 * The mobile citizen BFF surface (/internal/v1/mobile/citizen/telehealth/*)
 * does not expose media-token / waiting-room routes, so the app calls the
 * shared /internal/v1/teleconsult/* contract directly — the api client
 * injects the same trust headers either way.
 */

const TELECONSULT_V1 = "/internal/v1/teleconsult";

export type MediaTokenStatus = "READY" | "WAITING" | "DENIED";

export interface MediaTokenResult {
  status: MediaTokenStatus;
  roomUrl?: string;
  token?: string;
  /** Optional human-readable reason returned with a DENIED decision. */
  reason?: string;
}

export interface MediaTokenRequest {
  displayName: string;
  role: "PATIENT" | "PROVIDER";
  mediaProfile?: "AUDIO_ONLY" | "AUDIO_VIDEO";
}

interface MediaTokenEnvelope {
  data?: MediaTokenRow;
}

interface MediaTokenRow {
  status?: string;
  room_url?: string;
  roomUrl?: string;
  token?: string;
  accessToken?: string;
  access_token?: string;
  reason?: string;
  denied_reason?: string;
}

export function normalizeMediaTokenPayload(payload: MediaTokenEnvelope | MediaTokenRow | null | undefined): MediaTokenResult {
  const row: MediaTokenRow = ((payload as MediaTokenEnvelope | undefined)?.data ?? payload ?? {}) as MediaTokenRow;
  const status = String(row.status ?? "").toUpperCase();
  if (status === "DENIED") {
    return { status: "DENIED", reason: row.reason ?? row.denied_reason };
  }
  if (status === "WAITING") {
    return { status: "WAITING" };
  }
  const roomUrl = row.room_url ?? row.roomUrl;
  const token = row.token ?? row.accessToken ?? row.access_token;
  if (roomUrl && token) {
    return { status: "READY", roomUrl, token };
  }
  // No decision and no credentials yet — treat as still waiting so callers keep polling.
  return { status: "WAITING" };
}

function mediaTokenBody(request: MediaTokenRequest): Record<string, unknown> {
  return {
    displayName: request.displayName,
    role: request.role,
    ...(request.mediaProfile ? { mediaProfile: request.mediaProfile } : {}),
  };
}

export async function requestSessionMediaToken(
  id: string,
  request: MediaTokenRequest
): Promise<MediaTokenResult> {
  const response = await apiClient.post<MediaTokenEnvelope>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(id)}/media/token`,
    mediaTokenBody(request)
  );
  return normalizeMediaTokenPayload(response.data);
}

export async function refreshSessionMediaToken(
  id: string,
  request: MediaTokenRequest
): Promise<MediaTokenResult> {
  const response = await apiClient.post<MediaTokenEnvelope>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(id)}/media/token/refresh`,
    mediaTokenBody(request)
  );
  return normalizeMediaTokenPayload(response.data);
}
