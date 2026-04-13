/**
 * Experience UI — CDS Alerts Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useCDSAlerts() {
  return useQuery<ApiResponse<{ count: number }>>({
    queryKey: ["cds-alerts"],
    queryFn: () => apiClient.get("/internal/v1/cds/alerts/count"),
    refetchInterval: 30_000, // refresh every 30s
  });
}
