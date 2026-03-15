/**
 * Prescription Service — Citizen prescription and refill management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/prescriptions/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Prescription } from "../types";

const V1 = "/internal/v1/mobile/citizen/prescriptions";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchPrescriptions(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: Prescription[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<Prescription>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchPrescription(id: string): Promise<Prescription> {
  const response = await apiClient.get<{ data: Prescription }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}

export async function requestRefill(prescriptionId: string, params: {
  pharmacyId?: string;
  notes?: string;
} = {}): Promise<{ refillId: string; status: string }> {
  const response = await apiClient.post<{ data: { refillId: string; status: string } }>(
    `${V1}/${encodeURIComponent(prescriptionId)}/refill`,
    params
  );
  return response.data.data;
}
