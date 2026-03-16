/**
 * Records Service — Citizen medical records / clinical documents.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/records/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { MedicalRecord, RecordType } from "../types";

const V1 = "/internal/v1/mobile/citizen/records";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function getRecords(params: {
  type?: RecordType;
  page?: number;
  size?: number;
} = {}): Promise<{ items: MedicalRecord[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.type) query.push(`type=${params.type}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<MedicalRecord>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function getRecordDetail(id: string): Promise<MedicalRecord> {
  const response = await apiClient.get<{ data: MedicalRecord }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}
