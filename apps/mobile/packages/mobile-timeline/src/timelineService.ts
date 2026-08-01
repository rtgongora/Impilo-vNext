/**
 * Timeline Service — Fetches and manages timeline data from the backend.
 *
 * Backend: experience-bff (/internal/v1/mobile/timeline/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { TimelineFilters, TimelineResponse } from "./types";
import { normalizeEvents } from "./normalizers";

const CITIZEN_V1 = "/internal/v1/mobile/citizen/timeline";

interface TimelineRow {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

function normalizeRows(rows: TimelineRow[]): ReturnType<typeof normalizeEvents> {
  return normalizeEvents(rows.map((row) => ({
    id: row.id,
    resourceType: String(row.attributes.eventType ?? row.attributes.event_type ?? row.type),
    timestamp: String(row.attributes.occurredAt ?? row.attributes.occurred_at ?? ""),
    data: {
      ...row.attributes,
      summary: row.attributes.description,
      sourceId: row.attributes.sourceId ?? row.attributes.source_id,
    },
    source: String(row.attributes.sourceType ?? row.attributes.source_type ?? "experience-bff"),
  })));
}

function toBackendEventType(type: string): string {
  if (type === "VISIT") return "ENCOUNTER";
  if (type === "CARE_MILESTONE") return "JOURNEY_OPENED";
  return type;
}

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
    data: TimelineRow[];
    meta?: { page?: { number: number; total_pages: number; total_elements: number } };
  }>(`/internal/v1/timeline?patient_id=${encodeURIComponent(cpid)}${qs ? `&${qs.slice(1)}` : ""}`);
  const normalized = normalizeRows(response.data.data ?? []);
  const pageMeta = response.data.meta?.page;
  const hasMore = pageMeta ? pageMeta.number + 1 < pageMeta.total_pages : false;

  return {
    items: normalized,
    cursor: hasMore && pageMeta ? String(pageMeta.number + 1) : undefined,
    hasMore,
    totalElements: pageMeta?.total_elements,
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
  if (filters?.types?.length === 1) query.push(`eventType=${toBackendEventType(filters.types[0])}`);
  if (filters?.startDate) query.push(`startDate=${encodeURIComponent(filters.startDate)}`);
  if (filters?.endDate) query.push(`endDate=${encodeURIComponent(filters.endDate)}`);
  if (filters?.facilityId) query.push(`facilityId=${encodeURIComponent(filters.facilityId)}`);
  const page = options?.cursor ? Number.parseInt(options.cursor, 10) : 0;
  query.push(`page=${Number.isFinite(page) ? page : 0}`);
  if (options?.size) query.push(`size=${options.size}`);

  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<{
    data: TimelineRow[];
    meta: { page?: { number: number; total_pages: number; total_elements: number } };
  }>(`${CITIZEN_V1}${qs}`);
  const normalized = normalizeRows(response.data.data ?? []);
  const pageMeta = response.data.meta?.page;
  const hasMore = pageMeta ? pageMeta.number + 1 < pageMeta.total_pages : false;

  return {
    items: normalized,
    cursor: hasMore && pageMeta ? String(pageMeta.number + 1) : undefined,
    hasMore,
    totalElements: pageMeta?.total_elements,
  };
}
