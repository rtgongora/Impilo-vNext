"use client";

/**
 * Daidzai EMS clinical dispatch + prehospital ePCR (WU4/WU5).
 *
 * Consumes the experience-bff Daidzai passthroughs (/internal/v1/daidzai/*), which
 * proxy daidzai-service EmsMissionController, and the pct-backed ED pre-arrival board.
 * The EMS mission is a validated state machine; ePCR obs land on the ED pre-arrival
 * projection BEFORE the ambulance arrives. Nothing here is mocked.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const DZ = "/internal/v1/daidzai";
const ED = "/internal/v1/ed";

export type EmsMission = Record<string, unknown> & {
  id?: string;
  missionReference?: string;
  incidentId?: string;
  traumaEpisodeId?: string;
  state?: string;
  dispatchPriority?: string;
  destinationFacilityId?: string;
  pctEncounterRef?: string;
  dispatchedAt?: string;
  onSceneAt?: string;
  handoverAt?: string;
};

export type EmsEpcr = Record<string, unknown> & {
  epcrId?: string;
  missionId?: string;
  traumaEpisodeId?: string;
  patientHealthId?: string;
  narrative?: string;
  events?: Array<Record<string, unknown>>;
  snapshot?: Record<string, unknown>;
};

/** Ordered EMS mission lifecycle (daidzai EmsMissionState). */
export const EMS_STATES = [
  "CREATED",
  "DISPATCHED",
  "ACKNOWLEDGED",
  "ACCEPTED",
  "EN_ROUTE_SCENE",
  "ON_SCENE",
  "PATIENT_CONTACT",
  "DEPARTED_SCENE",
  "EN_ROUTE_FACILITY",
  "ARRIVED_FACILITY",
  "HANDOVER",
  "CLEARED",
] as const;
export type EmsState = (typeof EMS_STATES)[number];

/** The next legal forward state, or null at a terminal state. */
export function nextEmsState(state?: string): EmsState | null {
  const i = EMS_STATES.indexOf((state ?? "") as EmsState);
  if (i < 0 || i >= EMS_STATES.length - 1) return null;
  return EMS_STATES[i + 1];
}

export function useEmsMission(missionId?: string) {
  return useQuery({
    queryKey: ["ems-mission", missionId],
    queryFn: async () => apiClient.get<EmsMission>(`${DZ}/ems/missions/${missionId}`),
    enabled: !!missionId,
    refetchInterval: 10_000,
  });
}

export function useEmsMissionByIncident(incidentId?: string) {
  return useQuery({
    queryKey: ["ems-mission-by-incident", incidentId],
    queryFn: async () => apiClient.get<EmsMission>(`${DZ}/ems/incidents/${incidentId}/mission`),
    enabled: !!incidentId,
    retry: false,
  });
}

export function useEmsEpcr(missionId?: string) {
  return useQuery({
    queryKey: ["ems-epcr", missionId],
    queryFn: async () => apiClient.get<EmsEpcr>(`${DZ}/ems/missions/${missionId}/epcr`),
    enabled: !!missionId,
    retry: false,
  });
}

export function useEmsMissionActions(missionId?: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["ems-mission", missionId] });
    void qc.invalidateQueries({ queryKey: ["ems-epcr", missionId] });
    void qc.invalidateQueries({ queryKey: ["ed-pre-arrival"] });
  };
  return {
    advance: useMutation({
      mutationFn: (body: { toState: string; note?: string; pctEncounterRef?: string }) =>
        apiClient.post(`${DZ}/ems/missions/${missionId}/advance`, body),
      onSuccess: invalidate,
    }),
    upsertEpcr: useMutation({
      mutationFn: (body: { patientHealthId?: string; primarySurvey?: Record<string, unknown>; narrative?: string }) =>
        apiClient.post(`${DZ}/ems/missions/${missionId}/epcr`, body),
      onSuccess: invalidate,
    }),
    addEpcrEvent: useMutation({
      mutationFn: (body: { eventType: string; channel: string; payload: Record<string, unknown> }) =>
        apiClient.post(`${DZ}/ems/missions/${missionId}/epcr/events`, body),
      onSuccess: invalidate,
    }),
  };
}

/** Dispatch an EMS mission for an incident (idempotent per incident). */
export function useDispatchEmsMission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ incidentId, ...body }: { incidentId: string; callSign?: string; ambulanceAssetId?: string; priority?: string }) =>
      apiClient.post<EmsMission>(`${DZ}/ems/incidents/${incidentId}/dispatch`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ems-mission-by-incident"] });
      void qc.invalidateQueries({ queryKey: ["daidzai-incidents"] });
    },
  });
}

/** ED pre-arrival board — inbound EMS patients with prehospital snapshots, before arrival. */
export function useEdPreArrival(facilityId?: string) {
  const qs = facilityId ? `?facilityId=${encodeURIComponent(facilityId)}` : "";
  return useQuery({
    queryKey: ["ed-pre-arrival", facilityId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: Array<Record<string, unknown>> }>(`${ED}/pre-arrival${qs}`);
      return res.data ?? [];
    },
    refetchInterval: 15_000,
  });
}

/** Blood-readiness gate for a MADI order (read-through of MADI truth; never false-ready). */
export function useBloodReadiness(orderId?: string) {
  return useQuery({
    queryKey: ["blood-readiness", orderId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: Record<string, unknown> }>(`${ED}/blood-readiness?orderId=${encodeURIComponent(orderId ?? "")}`);
      return res.data;
    },
    enabled: !!orderId,
    refetchInterval: 20_000,
  });
}
