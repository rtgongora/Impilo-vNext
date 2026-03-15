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
}): Promise<TelehealthSession> {
  const response = await apiClient.post<{ data: TelehealthSession }>(`${V1}/sessions`, params);
  return response.data.data;
}

export async function joinSession(
  id: string
): Promise<{ session: TelehealthSession; token: string; channel: string }> {
  const response = await apiClient.post<{
    data: { attributes: TelehealthSession & { token: string; channel: string } };
  }>(`${V1}/sessions/${encodeURIComponent(id)}/join`);
  const attrs = response.data.data.attributes;
  return {
    session: attrs,
    token: attrs.token,
    channel: attrs.channel,
  };
}

export async function endSession(id: string, notes?: string): Promise<void> {
  await apiClient.post(`${V1}/sessions/${encodeURIComponent(id)}/end`, { notes });
}
