/**
 * Experience UI — Pharmacy Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PrescriptionResource {
  id: string;
  type: "prescription";
  attributes: {
    patientId: string;
    prescriberId: string;
    status: string;
    items: Array<{
      medication: string;
      dosage: string;
      quantity: number;
    }>;
    [key: string]: unknown;
  };
}

export interface DispenseResult {
  id: string;
  type: "dispense";
  attributes: {
    prescriptionId: string;
    dispensedAt: string;
    dispensedBy: string;
    [key: string]: unknown;
  };
}

interface PrescriptionsParams {
  patientId?: string;
  status?: string;
}

interface DispensePayload {
  prescriptionId: string;
  [key: string]: unknown;
}

type PrescriptionsResponse = ApiResponse<PrescriptionResource[]>;
type DispenseResponse = ApiResponse<DispenseResult>;

export function usePrescriptions(params?: PrescriptionsParams) {
  return useQuery<PrescriptionsResponse>({
    queryKey: ["prescriptions", params],
    enabled: Boolean(params?.patientId),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.patientId) searchParams.set("patient_id", params.patientId);
      if (params?.status) searchParams.set("status", params.status);

      const qs = searchParams.toString();
      const path = `/internal/v1/pharmacy/prescriptions${qs ? `?${qs}` : ""}`;
      return apiClient.get<PrescriptionsResponse>(path);
    },
  });
}

export interface CreatePrescriptionPayload {
  patient_id: string;
  medication_name: string;
  dosage: string;
  frequency: string;
  prescribed_by: string;
  facility_id?: string;
  encounter_id?: string;
  generic_name?: string;
  route?: string;
  duration?: string;
  quantity?: number;
  instructions?: string;
  indication?: string;
}

/** Create a prescription via the pharmacy SoR (BFF `POST /internal/v1/pharmacy/prescriptions`). */
export function useCreatePrescription() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<PrescriptionResource>, unknown, CreatePrescriptionPayload>({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<PrescriptionResource>>("/internal/v1/pharmacy/prescriptions", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prescriptions"] });
    },
  });
}

export function useDispensePrescription() {
  const queryClient = useQueryClient();

  return useMutation<DispenseResponse, unknown, DispensePayload>({
    mutationFn: (payload: DispensePayload) =>
      apiClient.post<DispenseResponse>("/internal/v1/pharmacy/dispense", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["prescriptions"] });
    },
  });
}
