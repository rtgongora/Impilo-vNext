/**
 * Experience UI — Patients Query Hooks
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PatientResource {
  id: string;
  type: "patient";
  attributes: {
    displayName: string;
    dateOfBirth: string;
    gender: string;
    cpid: string;
    [key: string]: unknown;
  };
}

interface PatientsParams {
  search?: string;
  page?: number;
}

type PatientsResponse = ApiResponse<PatientResource[]>;
type PatientResponse = ApiResponse<PatientResource>;

export function usePatients(params?: PatientsParams) {
  return useQuery<PatientsResponse>({
    queryKey: ["patients", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.page !== undefined) searchParams.set("page", String(params.page));

      const qs = searchParams.toString();
      const path = `/internal/v1/patients${qs ? `?${qs}` : ""}`;
      return apiClient.get<PatientsResponse>(path);
    },
  });
}

export function usePatient(id: string) {
  return useQuery<PatientResponse>({
    queryKey: ["patients", id],
    queryFn: () => apiClient.get<PatientResponse>(`/internal/v1/patients/${id}`),
    enabled: !!id,
  });
}
