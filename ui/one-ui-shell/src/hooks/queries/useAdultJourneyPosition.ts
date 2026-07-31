"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export const ADULT_JOURNEY_STEP_KEYS = [
  "PRESENTING",
  "CLERKING",
  "EXAMINATION",
  "PROBLEMS",
  "INVESTIGATIONS",
  "DIAGNOSIS",
  "TREATMENT_PLAN",
  "MEDICINES",
  "PROCEDURES",
  "ADMISSION",
  "WARD_ROUND",
  "CONSULTATION",
  "MDT",
  "MONITORING",
  "DISCHARGE_PLANNING",
  "TRANSFER",
  "FOLLOW_UP",
] as const;

export type AdultJourneyStepKey = (typeof ADULT_JOURNEY_STEP_KEYS)[number];

export interface AdultJourneyPosition {
  id: string;
  subject_cpid: string;
  journey_id: string | null;
  encounter_id: string | null;
  step_key: AdultJourneyStepKey;
  step_index: number;
  notes: string | null;
  recorded_by: string | null;
  recorded_at: string | null;
}

export interface AdultJourneyPositionResponse {
  subject_cpid: string;
  step_keys: AdultJourneyStepKey[];
  position: AdultJourneyPosition | null;
  position_note?: string;
}

export function stepLabel(key: AdultJourneyStepKey): string {
  return key.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

export function useAdultJourneyPosition(
  patientId: string,
  opts?: { journeyId?: string; encounterId?: string },
) {
  const journeyId = opts?.journeyId;
  const encounterId = opts?.encounterId;
  const params = new URLSearchParams({ subject_cpid: patientId });
  if (journeyId) params.set("journey_id", journeyId);
  if (encounterId) params.set("encounter_id", encounterId);

  return useQuery<ApiResponse<AdultJourneyPositionResponse>>({
    queryKey: ["medicine", "journey-position", patientId, journeyId ?? null, encounterId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<AdultJourneyPositionResponse>>(
        `/internal/v1/medicine/journey-position?${params.toString()}`,
      ),
    enabled: !!patientId,
  });
}

export function useRecordAdultJourneyPosition(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      subject_cpid: string;
      step_key: AdultJourneyStepKey;
      step_index: number;
      journey_id?: string;
      encounter_id?: string;
      notes?: string;
    }) => apiClient.post<ApiResponse<AdultJourneyPosition>>("/internal/v1/medicine/journey-position", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["medicine", "journey-position", patientId] });
    },
  });
}
