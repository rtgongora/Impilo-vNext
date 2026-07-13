/**
 * Experience UI — Inpatient (Clinical Plane) Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type AdmissionListResponse = ApiResponse<unknown>;
type AdmissionDetailResponse = ApiResponse<unknown>;
type WardRoundsResponse = ApiResponse<unknown>;

export function useAdmissions(patientCpid?: string) {
  return useQuery<AdmissionListResponse>({
    queryKey: ["inpatient-admissions", patientCpid ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (patientCpid) searchParams.set("patient_cpid", patientCpid);
      const qs = searchParams.toString();
      const path = `/internal/v1/inpatient/admissions${qs ? `?${qs}` : ""}`;
      return apiClient.get<AdmissionListResponse>(path);
    },
  });
}

/** Pending SBAR shift handovers for a facility (inpatient-service ShiftHandoverEntity). */
export function useShiftHandovers(facilityId?: string | null, status: string = "PENDING") {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["inpatient-handovers", facilityId ?? null, status],
    queryFn: () => {
      const sp = new URLSearchParams();
      sp.set("facility_id", facilityId!);
      if (status) sp.set("status", status);
      return apiClient.get<ApiResponse<unknown>>(`/internal/v1/inpatient/handovers?${sp.toString()}`);
    },
    enabled: !!facilityId,
  });
}

/** Resolved current inpatient location (active admission's ward + bed labels). */
export interface PatientLocation {
  admissionRef: string;
  status: string;
  facilityId: string | null;
  wardId: string | null;
  wardName: string | null;
  bedId: string | null;
  bedNumber: string | null;
  admissionType: string | null;
  admittedAt: string | null;
}

/**
 * Current inpatient location for a patient — backs the experience-shell patient-location
 * badge (G053). Returns `data: null` when the patient is not currently admitted.
 * `facilityId` is optional (scopes the lookup when present).
 */
export function usePatientLocation(subjectCpid?: string | null, facilityId?: string | null) {
  return useQuery<ApiResponse<PatientLocation | null>>({
    queryKey: ["inpatient-current-location", subjectCpid ?? null, facilityId ?? null],
    queryFn: () => {
      const sp = new URLSearchParams();
      sp.set("subject_cpid", subjectCpid!);
      if (facilityId) sp.set("facility_id", facilityId);
      return apiClient.get<ApiResponse<PatientLocation | null>>(
        `/internal/v1/inpatient/admissions/current-location?${sp.toString()}`,
      );
    },
    enabled: !!subjectCpid,
  });
}

export function useActiveAdmission(subjectCpid?: string, facilityId?: string) {
  return useQuery<AdmissionDetailResponse>({
    queryKey: ["inpatient-active-admission", subjectCpid ?? null, facilityId ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (subjectCpid) searchParams.set("subject_cpid", subjectCpid);
      if (facilityId) searchParams.set("facility_id", facilityId);
      return apiClient.get<AdmissionDetailResponse>(
        `/internal/v1/inpatient/admissions/active?${searchParams.toString()}`,
      );
    },
    enabled: !!subjectCpid && !!facilityId,
  });
}

export function useAdmission(id?: string | number | null) {
  return useQuery<AdmissionDetailResponse>({
    queryKey: ["inpatient-admission", id],
    queryFn: () =>
      apiClient.get<AdmissionDetailResponse>(`/internal/v1/inpatient/admissions/${id}`),
    enabled: !!id,
  });
}

export function useWardRounds(admissionId?: string | number | null) {
  return useQuery<WardRoundsResponse>({
    queryKey: ["inpatient-ward-rounds", admissionId],
    queryFn: () =>
      apiClient.get<WardRoundsResponse>(
        `/internal/v1/inpatient/admissions/${admissionId}/ward-rounds`,
      ),
    enabled: !!admissionId,
  });
}

export function useCreateAdmission() {
  const queryClient = useQueryClient();

  return useMutation<AdmissionDetailResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<AdmissionDetailResponse>("/internal/v1/inpatient/admissions", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admissions"] });
      void queryClient.invalidateQueries({ queryKey: ["beds"] });
      void queryClient.invalidateQueries({ queryKey: ["wards"] });
      void queryClient.invalidateQueries({ queryKey: ["inpatient-active-admission"] });
    },
  });
}

/** Start a ward round for an admission (inpatient-service WardRoundController via BFF). */
export function useStartWardRound() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, Record<string, unknown> & { admissionRef: string }>({
    mutationFn: (body) => apiClient.post<ApiResponse<unknown>>("/internal/v1/ward-rounds", body),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-ward-rounds", variables.admissionRef] });
    },
  });
}

/** Add an entry (assessment / plan / orders) to an existing ward round. */
export function useAddWardRoundEntry() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<unknown>,
    unknown,
    { roundId: string; admissionRef: string; assessment: string; plan: string; newOrders?: string; escalation?: string; reviewedBy?: string }
  >({
    mutationFn: ({ roundId, ...body }) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/ward-rounds/${roundId}/entries`, body),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-ward-rounds", variables.admissionRef] });
    },
  });
}

export function useDischargeAdmission() {
  const queryClient = useQueryClient();

  return useMutation<AdmissionDetailResponse, unknown, { id: string | number; body?: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.post<AdmissionDetailResponse>(
        `/internal/v1/inpatient/admissions/${id}/discharge`,
        body,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admissions"] });
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admission", variables.id] });
      void queryClient.invalidateQueries({ queryKey: ["core-transaction"] });
      void queryClient.invalidateQueries({ queryKey: ["beds"] });
    },
  });
}

export function useTransferPatient() {
  const queryClient = useQueryClient();

  return useMutation<AdmissionDetailResponse, unknown, { id: string | number; body: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.post<AdmissionDetailResponse>(
        `/internal/v1/inpatient/admissions/${id}/transfer`,
        body,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admissions"] });
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admission", variables.id] });
      void queryClient.invalidateQueries({ queryKey: ["inpatient-ward-rounds", variables.id] });
      void queryClient.invalidateQueries({ queryKey: ["beds"] });
    },
  });
}

// ── Deterioration escalations (WS#6) ───────────────────────────────────────

export interface InpatientEscalation {
  escalation_id?: string;
  id?: string;
  subject_cpid?: string;
  ward_id?: string | null;
  total_score?: number;
  risk_level?: string;
  status?: string;
  response_due_at?: string | null;
  raised_at?: string | null;
}

/** Open deterioration escalations for a ward, or all for a patient. */
export function useEscalations(args: { patientId?: string; wardId?: string } = {}) {
  const { patientId, wardId } = args;
  return useQuery<ApiResponse<InpatientEscalation[]>>({
    queryKey: ["inpatient-escalations", patientId ?? null, wardId ?? null],
    queryFn: () => {
      const sp = new URLSearchParams();
      if (patientId) sp.set("patientId", patientId);
      if (wardId) sp.set("wardId", wardId);
      const qs = sp.toString();
      return apiClient.get<ApiResponse<InpatientEscalation[]>>(
        `/internal/v1/inpatient/escalations${qs ? `?${qs}` : ""}`,
      );
    },
    enabled: !!patientId || !!wardId,
  });
}

export function useAcknowledgeEscalation() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { escalationId: string }>({
    mutationFn: ({ escalationId }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/inpatient/escalations/${escalationId}/acknowledge`,
        {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-escalations"] });
    },
  });
}

export function useRespondEscalation() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { escalationId: string; note?: string }>({
    mutationFn: ({ escalationId, note }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/inpatient/escalations/${escalationId}/respond`,
        { note: note ?? "" },
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-escalations"] });
    },
  });
}

// ── Discharge summary (WS#6) ───────────────────────────────────────────────

export interface DischargeSummary {
  id?: string | null;
  encounter_id?: string;
  subject_cpid?: string;
  status?: string;
  discharge_diagnosis?: string | null;
  discharge_disposition?: string | null;
  hospital_course?: string | null;
  medication_reconciliation?: string | null;
  discharge_medications?: string | null;
  follow_up?: string | null;
  referrals?: string | null;
  fhir_composition?: string | null;
  countersign_required?: boolean;
  authored_by?: string | null;
  countersigned_by?: string | null;
  countersigned_at?: string | null;
  finalised_by?: string | null;
  finalised_at?: string | null;
}

export function useDischargeSummary(encounterId?: string | null) {
  return useQuery<ApiResponse<DischargeSummary>>({
    queryKey: ["inpatient-discharge-summary", encounterId ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<DischargeSummary>>(
        `/internal/v1/inpatient/discharge-summary?encounterId=${encounterId}`,
      ),
    enabled: !!encounterId,
  });
}

export function useSaveDischargeSummary() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<DischargeSummary>, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<DischargeSummary>>("/internal/v1/inpatient/discharge-summary", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-discharge-summary"] });
    },
  });
}

export function useFinaliseDischargeSummary() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<DischargeSummary>, unknown, { encounterId: string }>({
    mutationFn: ({ encounterId }) =>
      apiClient.post<ApiResponse<DischargeSummary>>(
        `/internal/v1/inpatient/discharge-summary/${encounterId}/finalise`,
        {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-discharge-summary"] });
      void queryClient.invalidateQueries({ queryKey: ["inpatient-admissions"] });
    },
  });
}

/** Countersign a discharge summary that requires supervisor countersignature before finalisation. */
export function useCountersignDischargeSummary() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<DischargeSummary>,
    unknown,
    { encounterId: string; attestation?: string }
  >({
    mutationFn: ({ encounterId, attestation }) =>
      apiClient.post<ApiResponse<DischargeSummary>>(
        `/internal/v1/inpatient/discharge-summary/${encounterId}/countersign`,
        attestation ? { attestation } : {},
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inpatient-discharge-summary"] });
    },
  });
}
