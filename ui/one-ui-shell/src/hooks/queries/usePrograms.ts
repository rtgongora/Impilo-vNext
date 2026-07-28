/**
 * Experience UI — HIV/TB care programme query hooks (W3).
 *
 * The BFF returns `{ data, meta }` on success and a non-2xx with an `error` code on failure. These
 * hooks surface the raw query state so a consumer can tell "not enrolled" from "could not be read":
 * a component that flattens `isError` to an empty list would report a patient as being in no
 * programme when the read simply failed — the exact defect the query-honesty guard exists to catch.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ProgrammeEnrolment {
  enrolment_id: string;
  subject_cpid: string;
  programme: "HIV_CARE" | "TB_TREATMENT" | string;
  status: string;
  exit_reason: string | null;
  anchor_problem_id: string;
  episode_id: string | null;
  programme_number: string | null;
  enrolled_on: string | null;
  exit_on: string | null;
  managing_facility_id: string | null;
  /** Derived by pct from the programme. HIV is on the confidential lane; TB is not. */
  confidential: boolean;
}

export interface TreatmentRegimen {
  regimen_id: string;
  enrolment_id: string;
  regimen_code: string;
  regimen_display: string | null;
  regimen_stage: string;
  started_on: string | null;
  ended_on: string | null;
  change_reason: string | null;
  current: boolean;
}

export interface ProgrammeAlert {
  code: string;
  name: string;
  severity: string;
  message: string;
  required_action: string;
  referral_required: boolean;
  dak_provenance?: { adaptation_type?: string; approving_authority?: string };
}

export interface ProgrammeGuidance {
  assessed: boolean;
  referral_required: boolean;
  incomplete: boolean;
  alerts: ProgrammeAlert[];
  not_assessed: string[];
  content_version: string | null;
  /**
   * Per fact: DERIVED_FROM_MEDICATION_LIST, CLINICIAN_ASSERTED or UNDETERMINED. A screen that shows
   * a clinician-asserted fact as if the system had detected it is telling them their own answer back
   * with the authority of a machine.
   */
  fact_provenance?: Record<string, string>;
  /** What could and could not be derived, in words. Present only when something could not be. */
  derivation_note?: string;
}

export function useProgrammeEnrolments(patientId: string, openOnly = false) {
  return useQuery<ApiResponse<ProgrammeEnrolment[]>>({
    queryKey: ["programmes", { patientId, openOnly }],
    queryFn: () =>
      apiClient.get<ApiResponse<ProgrammeEnrolment[]>>(
        `/internal/v1/programmes?patient_id=${encodeURIComponent(patientId)}&open_only=${openOnly}`
      ),
    enabled: !!patientId,
  });
}

export function useProgrammeRegimens(enrolmentId: string | null) {
  return useQuery<ApiResponse<TreatmentRegimen[]>>({
    queryKey: ["programme-regimens", { enrolmentId }],
    queryFn: () =>
      apiClient.get<ApiResponse<TreatmentRegimen[]>>(
        `/internal/v1/programmes/${enrolmentId}/regimens`
      ),
    enabled: !!enrolmentId,
  });
}

export interface EnrolProgrammePayload {
  patientId: string;
  programme: string;
  anchorProblemId: string;
  status?: string;
  programmeNumber?: string;
  managingFacilityId?: string;
  journeyId?: string;
  encounterId?: string;
  [key: string]: unknown;
}

export function useEnrolProgramme() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<ProgrammeEnrolment>, unknown, EnrolProgrammePayload>({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<ProgrammeEnrolment>>("/internal/v1/programmes", payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["programmes"] }),
  });
}

export function useExitProgramme() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<ProgrammeEnrolment>, unknown, { enrolmentId: string; exitReason: string; exitOn?: string }>({
    mutationFn: ({ enrolmentId, exitReason, exitOn }) =>
      apiClient.post<ApiResponse<ProgrammeEnrolment>>(`/internal/v1/programmes/${enrolmentId}/exit`, {
        exit_reason: exitReason,
        exit_on: exitOn,
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["programmes"] }),
  });
}

export function useStartRegimen() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<TreatmentRegimen>, unknown, { enrolmentId: string; payload: Record<string, unknown> }>({
    mutationFn: ({ enrolmentId, payload }) =>
      apiClient.post<ApiResponse<TreatmentRegimen>>(`/internal/v1/programmes/${enrolmentId}/regimens`, payload),
    onSuccess: (_r, { enrolmentId }) => {
      qc.invalidateQueries({ queryKey: ["programme-regimens", { enrolmentId }] });
      qc.invalidateQueries({ queryKey: ["programmes"] });
    },
  });
}

/**
 * Governed programme decision support. Advisory — the alert list is meaningful only alongside
 * `incomplete`/`not_assessed`: an empty alert list on an incomplete assessment is not an all-clear.
 */
export function useProgrammeGuidance() {
  return useMutation<ApiResponse<ProgrammeGuidance>, unknown, { programme: string; facts: Record<string, unknown> }>({
    mutationFn: ({ programme, facts }) =>
      apiClient.post<ApiResponse<ProgrammeGuidance>>(`/internal/v1/programmes/${programme}/guidance`, facts),
  });
}
