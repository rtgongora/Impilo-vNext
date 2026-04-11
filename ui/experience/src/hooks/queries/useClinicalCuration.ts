import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type ClinicalCurationStatus = "PROPOSED" | "APPROVED" | "REJECTED";

export interface ClinicalCurationReviewItem {
  id: string;
  review_status: ClinicalCurationStatus | string;
  proposed_payload?: Record<string, unknown>;
  [key: string]: unknown;
}

interface ClinicalCurationDecisionArgs {
  id: string;
  decision: "APPROVED" | "REJECTED";
  reviewer: string;
  notes?: string;
}

export function useClinicalCurationReviewItems(status: ClinicalCurationStatus) {
  return useQuery<ApiResponse<ClinicalCurationReviewItem[]>>({
    queryKey: ["clinical", "curation", "review-items", status],
    queryFn: () =>
      apiClient.get<ApiResponse<ClinicalCurationReviewItem[]>>(
        `/internal/v1/clinical/curation/review-items?status=${encodeURIComponent(status)}`,
      ),
  });
}

export function useClinicalCurationDecision(status: ClinicalCurationStatus) {
  const qc = useQueryClient();
  return useMutation<ApiResponse<Record<string, unknown>>, unknown, ClinicalCurationDecisionArgs>({
    mutationFn: ({ id, decision, reviewer, notes }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/clinical/curation/review-items/${encodeURIComponent(id)}/decision`,
        { decision, reviewer, notes: notes ?? "" },
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["clinical", "curation", "review-items", status] });
    },
  });
}
