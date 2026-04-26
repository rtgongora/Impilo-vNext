/**
 * Experience UI — Immunizations Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ImmunizationResource {
  id: string;
  type: "immunization";
  attributes: {
    patientId: string;
    vaccineName: string;
    vaccineCode: string | null;
    doseNumber: number | null;
    doseSequence: string | null;
    lotNumber: string | null;
    site: string | null;
    route: string | null;
    status: string;
    administeredBy: string;
    administeredAt: string;
    notes: string | null;
    createdAt: string;
  };
}

interface RecordImmunizationPayload {
  patientId: string;
  vaccineName: string;
  vaccineCode?: string | null;
  doseNumber?: number | null;
  doseSequence?: string | null;
  lotNumber?: string | null;
  site?: string | null;
  route?: string | null;
  administeredAt: string;
  notes?: string | null;
  [key: string]: unknown;
}

type ImmunizationsResponse = ApiResponse<ImmunizationResource[]>;
type ImmunizationResponse = ApiResponse<ImmunizationResource>;

export function useImmunizations(patientId: string) {
  return useQuery<ImmunizationsResponse>({
    queryKey: ["immunizations", { patientId }],
    queryFn: () =>
      apiClient.get<ImmunizationsResponse>(
        `/internal/v1/immunizations?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useRecordImmunization() {
  const queryClient = useQueryClient();

  return useMutation<ImmunizationResponse, unknown, RecordImmunizationPayload>({
    mutationFn: (payload: RecordImmunizationPayload) =>
      apiClient.post<ImmunizationResponse>("/internal/v1/immunizations", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["immunizations"] });
    },
  });
}
