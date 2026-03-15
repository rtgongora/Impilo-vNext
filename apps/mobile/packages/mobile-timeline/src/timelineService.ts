/**
 * Timeline Service — Fetches and manages timeline data from the backend.
 *
 * Backend: experience-bff (/internal/v1/mobile/timeline/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { TimelineFilters, TimelineResponse, RawBackendEvent } from "./types";
import { normalizeEvents } from "./normalizers";

const V1 = "/internal/v1/mobile/timeline";

/**
 * Fetch timeline events for a patient (CPID).
 */
export async function fetchTimeline(
  cpid: string,
  filters?: TimelineFilters,
  options?: { cursor?: string; size?: number }
): Promise<TimelineResponse> {
  const query: string[] = [];
  if (filters?.types?.length) query.push(`types=${filters.types.join(",")}`);
  if (filters?.startDate) query.push(`startDate=${encodeURIComponent(filters.startDate)}`);
  if (filters?.endDate) query.push(`endDate=${encodeURIComponent(filters.endDate)}`);
  if (filters?.facilityId) query.push(`facilityId=${encodeURIComponent(filters.facilityId)}`);
  if (filters?.sourceSystem) query.push(`sourceSystem=${encodeURIComponent(filters.sourceSystem)}`);
  if (options?.cursor) query.push(`cursor=${encodeURIComponent(options.cursor)}`);
  if (options?.size) query.push(`size=${options.size}`);

  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<{
    events: RawBackendEvent[];
    cursor?: string;
    hasMore: boolean;
    totalElements?: number;
  }>(`${V1}/${encodeURIComponent(cpid)}${qs}`);

  const normalized = normalizeEvents(response.data.events);

  return {
    items: normalized,
    cursor: response.data.cursor,
    hasMore: response.data.hasMore,
    totalElements: response.data.totalElements,
  };
}

/**
 * Fetch timeline events for the current authenticated user (self).
 */
export async function fetchMyTimeline(
  filters?: TimelineFilters,
  options?: { cursor?: string; size?: number }
): Promise<TimelineResponse> {
  const query: string[] = [];
  if (filters?.types?.length) query.push(`types=${filters.types.join(",")}`);
  if (filters?.startDate) query.push(`startDate=${encodeURIComponent(filters.startDate)}`);
  if (filters?.endDate) query.push(`endDate=${encodeURIComponent(filters.endDate)}`);
  if (filters?.facilityId) query.push(`facilityId=${encodeURIComponent(filters.facilityId)}`);
  if (options?.cursor) query.push(`cursor=${encodeURIComponent(options.cursor)}`);
  if (options?.size) query.push(`size=${options.size}`);

  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<{
    events: RawBackendEvent[];
    cursor?: string;
    hasMore: boolean;
    totalElements?: number;
  }>(`${V1}/me${qs}`);

  const normalized = normalizeEvents(response.data.events);

  return {
    items: normalized,
    cursor: response.data.cursor,
    hasMore: response.data.hasMore,
    totalElements: response.data.totalElements,
  };
}
