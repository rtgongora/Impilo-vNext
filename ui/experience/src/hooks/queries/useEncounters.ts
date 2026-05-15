/**
 * Experience UI — Encounters Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface EncounterResource {
  id: string;
  type: "encounter";
  attributes: {
    patientId: string;
    providerId: string;
    facilityId: string;
    status: string;
    startedAt: string;
    closedAt: string | null;
    encounterType: string;
    costa_bill_id?: string;
    discharge_type?: string;
    discharged_at?: string;
    [key: string]: unknown;
  };
}

interface CreateEncounterPayload {
  patientId: string;
  facilityId: string;
  encounterType: string;
  [key: string]: unknown;
}

type EncountersResponse = ApiResponse<EncounterResource[]>;
type EncounterResponse = ApiResponse<EncounterResource>;

export function useEncounters(patientId: string) {
  return useQuery<EncountersResponse>({
    queryKey: ["encounters", { patientId }],
    queryFn: () =>
      apiClient.get<EncountersResponse>(
        `/internal/v1/encounters?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useEncounter(id: string) {
  return useQuery<EncounterResponse>({
    queryKey: ["encounters", id],
    queryFn: () => apiClient.get<EncounterResponse>(`/internal/v1/encounters/${id}`),
    enabled: !!id,
  });
}

export function useCreateEncounter() {
  const queryClient = useQueryClient();

  return useMutation<EncounterResponse, unknown, CreateEncounterPayload>({
    mutationFn: (payload: CreateEncounterPayload) =>
      apiClient.post<EncounterResponse>("/internal/v1/encounters", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["encounters"] });
    },
  });
}

export function useCloseEncounter() {
  const queryClient = useQueryClient();

  return useMutation<EncounterResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<EncounterResponse>(`/internal/v1/encounters/${id}/close`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["encounters"] });
    },
  });
}

export function useUpdateEncounterPathwayProtocol() {
  const queryClient = useQueryClient();

  return useMutation<
    EncounterResponse,
    unknown,
    { id: string; pathway_ref?: string | null; protocol_ref?: string | null }
  >({
    mutationFn: ({ id, pathway_ref, protocol_ref }) =>
      apiClient.patch<EncounterResponse>(`/internal/v1/encounters/${id}/pathway-protocol`, {
        pathway_ref: pathway_ref ?? null,
        protocol_ref: protocol_ref ?? null,
      }),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["encounters", vars.id] });
      queryClient.invalidateQueries({ queryKey: ["encounters"] });
    },
  });
}
