/**
 * Maternity summary — BFF /internal/v1/maternity/summary → PCT.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useMaternitySummary(patientId: string, encounterId?: string | null) {
  return useQuery({
    queryKey: ["maternity-summary", patientId, encounterId ?? null],
    queryFn: () => {
      const qs = new URLSearchParams({ patientId });
      if (encounterId) qs.set("encounterId", encounterId);
      return apiClient.get<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/maternity/summary?${qs.toString()}`
      );
    },
    enabled: !!patientId,
  });
}
