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
