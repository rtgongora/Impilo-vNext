/**
 * Experience UI — Encounter History Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useEncounterHistory(encounterId: string) {
  return useQuery<ApiResponse<Record<string, unknown>>>({
    queryKey: ["encounter-history", encounterId],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/encounter/${encodeURIComponent(encounterId)}/history`
      ),
    enabled: !!encounterId,
  });
}
