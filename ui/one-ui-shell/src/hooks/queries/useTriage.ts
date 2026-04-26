/**
 * Experience UI — Triage Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useTriage(encounterId: string) {
  return useQuery<ApiResponse<any>>({
    queryKey: ["triage", encounterId],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/triage?encounter_id=${encodeURIComponent(encounterId)}`
      ),
    enabled: !!encounterId,
  });
}
