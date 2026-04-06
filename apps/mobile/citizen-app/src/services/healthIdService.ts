/**
 * Health ID Service — Digital health card management.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { HealthId } from "../types";

const V1 = "/internal/v1/mobile/citizen/health-id";

export async function fetchHealthId(patientId: string): Promise<HealthId | null> {
  const response = await apiClient.get<{ data: HealthId | Record<string, never> }>(`${V1}?patientId=${patientId}`);
  const data = response.data.data;
  if (!data || !("healthIdNumber" in data)) return null;
  return data as HealthId;
}

export async function createHealthId(body: {
  patientId: string;
  bloodType?: string;
  allergiesSummary?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}): Promise<{ id: string; healthIdNumber: string }> {
  const response = await apiClient.post<{ data: { id: string; healthIdNumber: string } }>(V1, body);
  return response.data.data;
}
