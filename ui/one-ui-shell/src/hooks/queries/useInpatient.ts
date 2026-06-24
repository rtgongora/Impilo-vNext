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
