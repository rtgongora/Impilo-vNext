/**
 * Professional Pages Service — Healthcare provider profiles.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { ProfessionalPage } from "../types";

const V1 = "/internal/v1/mobile/citizen/providers";

export async function fetchProviders(specialty?: string): Promise<ProfessionalPage[]> {
  const params = specialty ? `?specialty=${specialty}` : "";
  const response = await apiClient.get<{ data: ProfessionalPage[] }>(`${V1}${params}`);
  return response.data.data;
}

export async function fetchProvider(id: string): Promise<ProfessionalPage> {
  const response = await apiClient.get<{ data: ProfessionalPage }>(`${V1}/${id}`);
  return response.data.data;
}
