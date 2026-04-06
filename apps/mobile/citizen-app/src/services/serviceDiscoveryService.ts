/**
 * Service Discovery — Find healthcare facilities and services.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/citizen/services/discover";

export interface DiscoverableService {
  id: string;
  sourceType: string;
  name: string;
  description?: string;
  category: string;
  facilityName?: string;
  price?: number;
  currency: string;
  available: boolean;
  rating?: number;
}

export async function discoverServices(query?: string, category?: string): Promise<DiscoverableService[]> {
  const params = new URLSearchParams();
  if (query) params.set("query", query);
  if (category) params.set("category", category);
  const qs = params.toString() ? `?${params.toString()}` : "";
  const response = await apiClient.get<{ data: DiscoverableService[] }>(`${V1}${qs}`);
  return response.data.data;
}
