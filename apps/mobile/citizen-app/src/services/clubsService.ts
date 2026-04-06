/**
 * Clubs Service — Wellness and fitness club membership.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { WellnessClub } from "../types";

const V1 = "/internal/v1/mobile/citizen/clubs";

export async function fetchClubs(category?: string): Promise<WellnessClub[]> {
  const params = category ? `?category=${category}` : "";
  const response = await apiClient.get<{ data: WellnessClub[] }>(`${V1}${params}`);
  return response.data.data;
}

export async function joinClub(clubId: string, patientId: string): Promise<void> {
  await apiClient.post(`${V1}/${clubId}/join`, { patientId });
}
