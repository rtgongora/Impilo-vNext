/**
 * TanStack Query hooks for EHR discharge workflow.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface DischargeRequest {
  encounterId: string;
  discharge_type: string;
  discharge_diagnosis?: string;
  treatment_summary?: string;
  follow_up_instructions?: string;
  medications_at_discharge?: string;
  patient_instructions?: string;
  discharged_by?: string;
}

export interface DischargeResource {
  id: string;
  type: string;
  attributes: {
    encounter_id: string;
    status: string;
    discharge_type: string;
    discharged_at: string;
    discharge_diagnosis?: string;
    treatment_summary?: string;
    follow_up_instructions?: string;
    medications_at_discharge?: string;
    patient_instructions?: string;
  };
}

export function useDischargeEncounter() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: DischargeRequest) => {
      return apiClient.post<ApiResponse<DischargeResource>>(
        `/internal/v1/encounters/${data.encounterId}/discharge`,
        {
          discharge_type: data.discharge_type,
          discharge_diagnosis: data.discharge_diagnosis ?? null,
          treatment_summary: data.treatment_summary ?? null,
          follow_up_instructions: data.follow_up_instructions ?? null,
          medications_at_discharge: data.medications_at_discharge ?? null,
          patient_instructions: data.patient_instructions ?? null,
          discharged_by: data.discharged_by ?? "current-user",
        },
      );
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["encounters", { patientId: undefined }],
      });
      queryClient.invalidateQueries({
        queryKey: ["encounter", variables.encounterId],
      });
    },
  });
}
