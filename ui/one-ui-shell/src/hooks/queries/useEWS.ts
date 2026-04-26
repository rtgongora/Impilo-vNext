/**
 * Early warning scores (NEWS2 / EWS) — CareEmergencyInpatientController.
 * GET  /internal/v1/ews?patientId=
 * POST /internal/v1/ews
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** JdbcTemplate row shape (snake_case). */
export type EarlyWarningScoreRow = Record<string, unknown> & {
  id?: string;
  total_score?: number;
  risk_level?: string;
  score_type?: string;
  recorded_at?: string;
  escalation_required?: boolean;
  recorded_by?: string | null;
};

type EwsListResponse = { data: EarlyWarningScoreRow[] };

export function useEarlyWarningScores(patientId: string | undefined) {
  return useQuery<EwsListResponse>({
    queryKey: ["ews", patientId],
    queryFn: () =>
      apiClient.get<EwsListResponse>(`/internal/v1/ews?patientId=${encodeURIComponent(patientId!)}`),
    enabled: Boolean(patientId),
    staleTime: 30_000,
  });
}

export function useRecordEWS() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      patientId: string;
      encounterId?: string | null;
      totalScore: number;
      scoreType?: string;
      recordedBy: string;
    }) =>
      apiClient.post<{ data: { id: string; riskLevel: string; escalationRequired: boolean } }>(
        "/internal/v1/ews",
        {
          patientId: body.patientId,
          encounterId: body.encounterId ?? null,
          totalScore: body.totalScore,
          scoreType: body.scoreType ?? "NEWS2",
          recordedBy: body.recordedBy,
        },
      ),
    onSuccess: (_, v) => {
      queryClient.invalidateQueries({ queryKey: ["ews", v.patientId] });
    },
  });
}
