/**
 * Experience UI — Vitals Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface VitalsResource {
  id: string;
  type: "vitals";
  attributes: {
    patientId: string;
    encounterId: string;
    recordedBy: string;
    systolic: number | null;
    diastolic: number | null;
    heartRate: number | null;
    temperature: number | null;
    respiratoryRate: number | null;
    oxygenSaturation: number | null;
    weight: number | null;
    height: number | null;
    bmi: number | null;
    painScore: number | null;
    notes: string | null;
    recordedAt: string;
  };
}

interface RecordVitalsPayload {
  patientId: string;
  encounterId: string;
  systolic?: number | null;
  diastolic?: number | null;
  heartRate?: number | null;
  temperature?: number | null;
  respiratoryRate?: number | null;
  oxygenSaturation?: number | null;
  weight?: number | null;
  height?: number | null;
  painScore?: number | null;
  notes?: string | null;
  [key: string]: unknown;
}

type VitalsListResponse = ApiResponse<VitalsResource[]>;
type VitalsResponse = ApiResponse<VitalsResource>;

export function useVitals(patientId: string) {
  return useQuery<VitalsListResponse>({
    queryKey: ["vitals", { patientId }],
    queryFn: () =>
      apiClient.get<VitalsListResponse>(
        `/internal/v1/vitals?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useEncounterVitals(encounterId: string) {
  return useQuery<VitalsListResponse>({
    queryKey: ["vitals", { encounterId }],
    queryFn: () =>
      apiClient.get<VitalsListResponse>(
        `/internal/v1/vitals?encounter_id=${encodeURIComponent(encounterId)}`
      ),
    enabled: !!encounterId,
  });
}

export function useRecordVitals() {
  const queryClient = useQueryClient();

  return useMutation<VitalsResponse, unknown, RecordVitalsPayload>({
    mutationFn: (payload: RecordVitalsPayload) =>
      apiClient.post<VitalsResponse>("/internal/v1/vitals", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["vitals"] });
    },
  });
}
