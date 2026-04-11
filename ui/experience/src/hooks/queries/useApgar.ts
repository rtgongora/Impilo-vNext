/**
 * Neonatal APGAR — persisted in `apgar_scores` (V27) via CareEmergencyInpatientController.
 * GET/POST /internal/v1/apgar
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** BFF returns JDBC row shape (snake_case). */
export interface ApgarScoreRow {
  id?: string;
  patient_id?: string;
  encounter_id?: string | null;
  minute: number;
  appearance: number;
  pulse: number;
  grimace: number;
  activity: number;
  respiration: number;
  total?: number;
  recorded_at?: string;
}

type ApgarListResponse = ApiResponse<ApgarScoreRow[]>;

export function useApgarScores(patientId: string) {
  return useQuery<ApgarListResponse>({
    queryKey: ["apgar", { patientId }],
    queryFn: () =>
      apiClient.get<ApgarListResponse>(`/internal/v1/apgar?patientId=${encodeURIComponent(patientId)}`),
    enabled: !!patientId,
  });
}

export interface RecordApgarPayload {
  patientId: string;
  encounterId?: string | null;
  minute: number;
  appearance: number;
  pulse: number;
  grimace: number;
  activity: number;
  respiration: number;
}

export function useRecordApgar() {
  const queryClient = useQueryClient();

  return useMutation<ApiResponse<{ id: string }>, unknown, RecordApgarPayload>({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<{ id: string }>>("/internal/v1/apgar", {
        patientId: payload.patientId,
        encounterId: payload.encounterId ?? null,
        minute: payload.minute,
        appearance: payload.appearance,
        pulse: payload.pulse,
        grimace: payload.grimace,
        activity: payload.activity,
        respiration: payload.respiration,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["apgar"] });
    },
  });
}
