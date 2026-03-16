/**
 * Health Timeline Service — Unified citizen health timeline.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/timeline/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { TimelineEntry, TimelineEntryType } from "../types";

const V1 = "/internal/v1/mobile/citizen/timeline";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function getTimeline(params: {
  type?: TimelineEntryType;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: TimelineEntry[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.type) query.push(`type=${params.type}`);
  if (params.from) query.push(`from=${params.from}`);
  if (params.to) query.push(`to=${params.to}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<TimelineEntry>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}
