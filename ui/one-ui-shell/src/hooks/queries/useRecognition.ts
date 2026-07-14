/**
 * Health-worker recognition query — BFF dual-source composition
 * (VARAPI licensed providers + Vashandi workforce).
 *
 * Display-only truth: powers the "Recognised Health Provider / Worker"
 * badge chip and the profile benefits card. Advantages driven by this
 * signal are administrative/financial ONLY — never clinical-triage
 * priority, never queue reordering.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type RecognitionClass = "LICENSED_PROVIDER" | "HEALTH_WORKFORCE" | "BOTH";

export interface RecognitionView {
  recognised: boolean;
  recognitionClass: RecognitionClass | null;
  profession: string | null;
  cadre: string | null;
  licenceStatus: string | null;
}

interface RecognitionResponse {
  data: RecognitionView;
}

export function useRecognition(healthId: string | null | undefined) {
  return useQuery<RecognitionView>({
    queryKey: ["recognition", healthId],
    queryFn: async () => {
      const res = await apiClient.get<RecognitionResponse>(
        `/internal/v1/experience/recognition/${encodeURIComponent(healthId ?? "")}`,
      );
      return res.data;
    },
    enabled: !!healthId,
    // Mirror the BFF's short TTL; a licence suspension should surface quickly.
    staleTime: 30_000,
    retry: false,
  });
}

export function recognitionLabel(cls: RecognitionClass | null | undefined): string {
  return cls === "HEALTH_WORKFORCE"
    ? "Recognised Health Worker"
    : "Recognised Health Provider";
}
