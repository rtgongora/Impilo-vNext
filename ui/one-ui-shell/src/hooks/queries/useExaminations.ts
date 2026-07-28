"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Structured physical examinations (brief.md §7).
 *
 * The six states are the type's reason for existing. `NOT_EXAMINED`, `UNABLE_TO_EXAMINE` and
 * `DEFERRED` are not quieter kinds of `NORMAL`, and `wasExamined` is carried on every finding by the
 * server rather than derived here — a client that computed it would be a second copy of the rule,
 * and the copy that drifts is always the one that decides what the clinician sees.
 */

export type ExaminationState =
  | "NORMAL"
  | "ABNORMAL"
  | "NOT_EXAMINED"
  | "UNABLE_TO_EXAMINE"
  | "DEFERRED"
  | "UNCERTAIN";

export interface ExaminationFinding {
  region: string;
  state: ExaminationState;
  /** Server-computed. Never recomputed on the client. */
  was_examined: boolean;
  detail: string | null;
  finding_code: string | null;
  graphic: string | null;
  site: string | null;
  laterality: string | null;
}

export interface ExaminationCoverage {
  examined: string[];
  recorded_not_examined: string[];
  /** Regions nobody considered at all — absent from the record, so invisible unless named. */
  never_considered: string[];
  note: string;
}

export interface Examination {
  examination_id: string;
  subject_cpid: string;
  examined_at: string;
  examined_by: string;
  context_note: string | null;
  findings: ExaminationFinding[];
  coverage: ExaminationCoverage;
}

export interface ExaminationSummary {
  examination_id: string;
  examined_at: string;
  examined_by: string;
  context_note: string | null;
}

export interface ExaminationVocabulary {
  regions: string[];
  states: ExaminationState[];
  examined_states: ExaminationState[];
  states_needing_detail: ExaminationState[];
  graphics: string[];
  laterality: string[];
  not_examined_is_not_normal: string;
}

/**
 * The vocabulary is fetched, not hardcoded. Two hand-maintained copies of a clinical vocabulary
 * drift, and the drift surfaces as a value the server refuses after a clinician has typed it.
 */
export function useExaminationVocabulary() {
  return useQuery<ApiResponse<ExaminationVocabulary>>({
    queryKey: ["examination-vocabulary"],
    queryFn: () => apiClient.get<ApiResponse<ExaminationVocabulary>>("/internal/v1/examinations/vocabulary"),
    staleTime: 60 * 60 * 1000,
  });
}

export function useExaminations(patientId: string) {
  return useQuery<ApiResponse<ExaminationSummary[]>>({
    queryKey: ["examinations", { patientId }],
    queryFn: () =>
      apiClient.get<ApiResponse<ExaminationSummary[]>>(
        `/internal/v1/examinations?patient_id=${encodeURIComponent(patientId)}`,
      ),
    enabled: !!patientId,
  });
}

export function useExamination(examinationId: string | null) {
  return useQuery<ApiResponse<Examination>>({
    queryKey: ["examination", { examinationId }],
    queryFn: () => apiClient.get<ApiResponse<Examination>>(`/internal/v1/examinations/${examinationId}`),
    enabled: !!examinationId,
  });
}

export interface RecordExaminationRequest {
  subject_cpid: string;
  journey_id?: string;
  encounter_id?: string;
  context_note?: string;
  findings: Array<{
    region: string;
    state: ExaminationState;
    detail?: string;
    graphic?: string;
    site?: string;
    laterality?: string;
  }>;
}

export function useRecordExamination(patientId: string) {
  const qc = useQueryClient();
  return useMutation<ApiResponse<Examination>, unknown, RecordExaminationRequest>({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<Examination>>("/internal/v1/examinations", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["examinations", { patientId }] });
    },
  });
}
