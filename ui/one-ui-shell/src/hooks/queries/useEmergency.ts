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

// ─────────────────────────────────────────────────────────────────────────────
// Resuscitation workspace (WU1) — ABCDE time-series on a live emergency activation.
// Backed by inpatient-service resuscitation_record / resuscitation_phase / emergency
// action rows, proxied by the experience-bff under /internal/v1/emergency/{id}/* and
// /internal/v1/ed/resuscitation/{id}. Every mutation stamps the canonical trauma
// episode via the X-Trauma-Episode-ID header (injected below) so the resus SoR rows
// re-key onto the same trauma_episode_id the rest of the journey carries.
// ─────────────────────────────────────────────────────────────────────────────

export type ResusRow = Record<string, unknown>;

/** ABCDE + arrest channels a responder streams during resuscitation. */
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
  return (body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T));
}

function episodeHeaders(traumaEpisodeId?: string): { extraHeaders?: Record<string, string> } | undefined {
  return traumaEpisodeId ? { extraHeaders: { "X-Trauma-Episode-ID": traumaEpisodeId } } : undefined;
}

/** Read-side of the resuscitation workspace: the record + phase / CPR / medication series. */
export function useResuscitation(activationId?: string) {
  const enabled = !!activationId;
  const record = useQuery({
    queryKey: ["resus-record", activationId],
    queryFn: async () => unwrap<ResusRow | null>(await apiClient.get(`/internal/v1/ed/resuscitation/${activationId}`)),
    enabled,
    retry: false,
  });
  const phases = useQuery({
    queryKey: ["resus-phases", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`/internal/v1/emergency/${activationId}/phases`)) ?? [],
    enabled,
  });
  const cprCycles = useQuery({
    queryKey: ["resus-cpr", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`/internal/v1/emergency/${activationId}/cpr-cycles`)) ?? [],
    enabled,
  });
  const medications = useQuery({
    queryKey: ["resus-meds", activationId],
    queryFn: async () => unwrap<ResusRow[]>(await apiClient.get(`/internal/v1/emergency/${activationId}/medications`)) ?? [],
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
    /** Record / update the resuscitation record (initial rhythm, CPR cycles, defibrillations). */
    recordResuscitation: useMutation({
      mutationFn: (body: { initialRhythm?: string; finalRhythm?: string; cprCycles?: number; defibrillations?: number; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/resuscitation`, body, opts),
      onSuccess: invalidate,
    }),
    /** Start an ABCDE / arrest phase block. */
    startPhase: useMutation({
      mutationFn: (body: { phase: ResusPhase | string; rhythm?: string; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/phases`, body, opts),
      onSuccess: invalidate,
    }),
    /** Close an open phase block. */
    endPhase: useMutation({
      mutationFn: ({ phaseId, ...body }: { phaseId: string; outcome?: string; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/phases/${phaseId}/end`, body, opts),
      onSuccess: invalidate,
    }),
    /** Log a completed CPR cycle. */
    addCprCycle: useMutation({
      mutationFn: (body: { cycleNumber?: number; rhythm?: string; shockDelivered?: boolean; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/cpr-cycles`, body, opts),
      onSuccess: invalidate,
    }),
    /** Record a resuscitation medication (adrenaline, amiodarone, TXA, …). */
    addMedication: useMutation({
      mutationFn: (body: { name: string; dose?: string; route?: string; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/medications`, body, opts),
      onSuccess: invalidate,
    }),
    /** Free-text timeline action / observation. */
    logAction: useMutation({
      mutationFn: (body: { actionType?: string; description: string; performedBy?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/action`, { actionType: "NOTE", ...body }, opts),
      onSuccess: invalidate,
    }),
    /** Stand the resuscitation down with an outcome. */
    end: useMutation({
      mutationFn: (body: { outcome: string; notes?: string }) =>
        apiClient.post(`/internal/v1/emergency/${activationId}/end`, body, opts),
      onSuccess: invalidate,
    }),
  };
}
