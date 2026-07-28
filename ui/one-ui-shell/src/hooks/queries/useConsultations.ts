"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Consultation and MDT (brief.md §14).
 *
 * `owning_service` and `owning_service_note` are carried on every consultation and must reach the
 * screen. The failure the record exists to prevent is a reader assuming the answering team took the
 * patient — §23.10's "loss of ownership" — and a type that drops the note quietly removes the only
 * thing on screen that says otherwise.
 */

export type ConsultationStatus = "REQUESTED" | "ACCEPTED" | "ANSWERED" | "DECLINED" | "WITHDRAWN";

export interface Consultation {
  consultation_id: string;
  subject_cpid: string;
  requesting_service: string;
  requesting_actor: string;
  asked_of_service: string;
  question: string;
  clinical_summary: string | null;
  urgency: string;
  status: ConsultationStatus;
  owning_service: string;
  owning_service_note: string;
  advice: string | null;
  responded_by: string | null;
  responded_at: string | null;
  takeover_recommended: boolean;
  takeover_note?: string;
  decline_reason: string | null;
  requested_at: string;
}

export interface MdtDecision {
  decision_id: string;
  meeting_type: string;
  met_on: string;
  chaired_by: string;
  participants: string;
  decision: string;
  /** null means the meeting did not record an intent — NOT the same as an intent of NOT_SET. */
  treatment_intent: string | null;
  next_action: string | null;
  responsible_service: string | null;
}

export function useConsultations(patientId: string) {
  return useQuery<ApiResponse<Consultation[]>>({
    queryKey: ["consultations", { patientId }],
    queryFn: () =>
      apiClient.get<ApiResponse<Consultation[]>>(
        `/internal/v1/consultations?patient_id=${encodeURIComponent(patientId)}`,
      ),
    enabled: !!patientId,
  });
}

export function useMdtDecisions(patientId: string) {
  return useQuery<ApiResponse<MdtDecision[]>>({
    queryKey: ["mdt-decisions", { patientId }],
    queryFn: () =>
      apiClient.get<ApiResponse<MdtDecision[]>>(
        `/internal/v1/consultations/mdt?patient_id=${encodeURIComponent(patientId)}`,
      ),
    enabled: !!patientId,
  });
}

export interface RequestConsultation {
  subject_cpid: string;
  journey_id?: string;
  encounter_id?: string;
  requesting_service: string;
  asked_of_service: string;
  question: string;
  clinical_summary?: string;
  urgency?: string;
}

export function useRequestConsultation(patientId: string) {
  const qc = useQueryClient();
  return useMutation<ApiResponse<Consultation>, unknown, RequestConsultation>({
    mutationFn: (body) => apiClient.post<ApiResponse<Consultation>>("/internal/v1/consultations", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["consultations", { patientId }] }),
  });
}

export function useAnswerConsultation(patientId: string) {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<Consultation>,
    unknown,
    { consultationId: string; advice: string; takeover_recommended?: boolean }
  >({
    mutationFn: ({ consultationId, ...body }) =>
      apiClient.post<ApiResponse<Consultation>>(
        `/internal/v1/consultations/${consultationId}/answer`,
        body,
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["consultations", { patientId }] }),
  });
}
