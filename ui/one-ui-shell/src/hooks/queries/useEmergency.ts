/**
 * Emergency / ED protocol activations — Experience BFF (existing persistence, V27+).
 *
 * GET  /internal/v1/emergency/activations
 * POST /internal/v1/emergency/activate
 * POST /internal/v1/emergency/{id}/action
 * POST /internal/v1/emergency/{id}/end
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Raw row shape from JdbcTemplate (snake_case keys). */
export type EmergencyActivationRow = Record<string, unknown> & {
  id?: string;
  patient_id?: string | null;
  encounter_id?: string | null;
  protocol_type?: string | null;
  status?: string | null;
  activation_time?: string | null;
  team_leader?: string | null;
  location?: string | null;
  outcome?: string | null;
  ended_at?: string | null;
};

type ActivationsPayload = { data: EmergencyActivationRow[] };

export function useEmergencyActivations() {
  return useQuery<ActivationsPayload>({
    queryKey: ["emergency-activations"],
    queryFn: () => apiClient.get<ActivationsPayload>("/internal/v1/emergency/activations"),
    staleTime: 15_000,
  });
}

export function useActivateEmergency() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      patientId?: string | null;
      encounterId?: string | null;
      protocolType: string;
      teamLeader?: string;
      location?: string;
    }) =>
      apiClient.post<{ data: { id: string; status: string } }>("/internal/v1/emergency/activate", {
        patientId: body.patientId || null,
        encounterId: body.encounterId || null,
        protocolType: body.protocolType,
        teamLeader: body.teamLeader ?? "",
        location: body.location ?? "",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["emergency-activations"] });
    },
  });
}

export function useLogEmergencyAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { id: string; actionType: string; description: string; performedBy: string }) =>
      apiClient.post<{ logged: boolean }>(`/internal/v1/emergency/${payload.id}/action`, {
        actionType: payload.actionType,
        description: payload.description,
        performedBy: payload.performedBy,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["emergency-activations"] });
    },
  });
}

export function useEndEmergency() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { id: string; outcome: string; notes: string }) =>
      apiClient.post<{ status: string }>(`/internal/v1/emergency/${payload.id}/end`, {
        outcome: payload.outcome,
        notes: payload.notes ?? "",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["emergency-activations"] });
    },
  });
}
