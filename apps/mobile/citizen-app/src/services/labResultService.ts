/**
 * Lab Result Service — Citizen lab result access.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/results/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { LabResult } from "../types";

const V1 = "/internal/v1/mobile/citizen/results";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchLabResults(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: LabResult[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<LabResult>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchLabResult(id: string): Promise<LabResult> {
  const response = await apiClient.get<{ data: LabResult }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}
