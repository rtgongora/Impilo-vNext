/**
 * Emergency / ED protocol activations — Experience BFF.
 *
 * Canonical surface: /internal/v1/ed/resuscitation/*
 * Deprecated aliases at /internal/v1/emergency/* remain for CareEmergencyInpatientController.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const RESUS = "/internal/v1/ed/resuscitation";

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
    queryFn: () => apiClient.get<ActivationsPayload>(`${RESUS}/activations`),
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
      apiClient.post<{ data: { id: string; status: string } }>(`${RESUS}/activate`, {
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
      apiClient.post<{ logged: boolean }>(`${RESUS}/${payload.id}/action`, {
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
      apiClient.post<{ status: string }>(`${RESUS}/${payload.id}/end`, {
        outcome: payload.outcome,
        notes: payload.notes ?? "",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["emergency-activations"] });
    },
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Resuscitation workspace — ABCDE time-series on a live activation.
// ─────────────────────────────────────────────────────────────────────────────

export type ResusRow = Record<string, unknown>;

export const RESUS_PHASES = [
  "AIRWAY",
  "BREATHING",
  "CIRCULATION",
  "DISABILITY",
  "EXPOSURE",
  "ARREST",
] as const;
export type ResusPhase = (typeof RESUS_PHASES)[number];

export const ARREST_RHYTHMS = ["VF", "PULSELESS_VT", "PEA", "ASYSTOLE", "ROSC"] as const;

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

function episodeHeaders(traumaEpisodeId?: string): { extraHeaders?: Record<string, string> } | undefined {
  return traumaEpisodeId ? { extraHeaders: { "X-Trauma-Episode-ID": traumaEpisodeId } } : undefined;
}

/** Read-side of the resuscitation workspace: the record + phase / CPR / medication series. */
export function useResuscitation(activationId?: string) {
  const enabled = !!activationId;
  const record = useQuery({
    queryKey: ["resus-record", activationId],
    queryFn: async () => unwrap<ResusRow | null>(await apiClient.get(`${RESUS}/${activationId}`)),
    enabled,
    retry: false,
  });
  const phases = useQuery({
    queryKey: ["resus-phases", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`${RESUS}/${activationId}/phases`)) ?? [],
    enabled,
  });
  const cprCycles = useQuery({
    queryKey: ["resus-cpr", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`${RESUS}/${activationId}/cpr-cycles`)) ?? [],
    enabled,
  });
  const medications = useQuery({
    queryKey: ["resus-meds", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`${RESUS}/${activationId}/medications`)) ?? [],
    enabled,
  });
  return { record, phases, cprCycles, medications };
}

/** Write-side of the resuscitation workspace. Every call carries the trauma episode header. */
export function useResuscitationActions(activationId: string, traumaEpisodeId?: string) {
  const qc = useQueryClient();
  const opts = episodeHeaders(traumaEpisodeId);
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["resus-record", activationId] });
    void qc.invalidateQueries({ queryKey: ["resus-phases", activationId] });
    void qc.invalidateQueries({ queryKey: ["resus-cpr", activationId] });
    void qc.invalidateQueries({ queryKey: ["resus-meds", activationId] });
  };

  return {
    recordResuscitation: useMutation({
      mutationFn: (body: {
        initialRhythm?: string;
        finalRhythm?: string;
        cprCycles?: number;
        defibrillations?: number;
        notes?: string;
      }) => apiClient.post(`${RESUS}/${activationId}/record`, body, opts),
      onSuccess: invalidate,
    }),
    startPhase: useMutation({
      mutationFn: (body: { phase: ResusPhase | string; rhythm?: string; notes?: string }) =>
        apiClient.post(`${RESUS}/${activationId}/phases`, body, opts),
      onSuccess: invalidate,
    }),
    endPhase: useMutation({
      mutationFn: ({ phaseId, ...body }: { phaseId: string; outcome?: string; notes?: string }) =>
        apiClient.post(`${RESUS}/${activationId}/phases/${phaseId}/end`, body, opts),
      onSuccess: invalidate,
    }),
    addCprCycle: useMutation({
      mutationFn: (body: {
        cycleNumber?: number;
        rhythm?: string;
        shockDelivered?: boolean;
        notes?: string;
      }) => apiClient.post(`${RESUS}/${activationId}/cpr-cycles`, body, opts),
      onSuccess: invalidate,
    }),
    addMedication: useMutation({
      mutationFn: (body: { name: string; dose?: string; route?: string; notes?: string }) =>
        apiClient.post(`${RESUS}/${activationId}/medications`, body, opts),
      onSuccess: invalidate,
    }),
    logAction: useMutation({
      mutationFn: (body: { actionType?: string; description: string; performedBy?: string }) =>
        apiClient.post(`${RESUS}/${activationId}/action`, { actionType: "NOTE", ...body }, opts),
      onSuccess: invalidate,
    }),
    end: useMutation({
      mutationFn: (body: { outcome: string; notes?: string }) =>
        apiClient.post(`${RESUS}/${activationId}/end`, body, opts),
      onSuccess: invalidate,
    }),
  };
}
