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

/**
 * Record an inbound pre-arrival notification, and convert one into a real ED visit on arrival.
 *
 * pct has served both writes since the ED lane was built; neither had a BFF route until W15c, so the
 * board above could only ever show rows some other path had created. A pre-arrival called in by
 * radio had nowhere to be typed.
 */
export function useEdPreArrivalActions(facilityId?: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["ed-pre-arrival", facilityId] });
    void qc.invalidateQueries({ queryKey: ["ed-pre-arrival"] });
  };

  return {
    notify: useMutation({
      mutationFn: (body: Record<string, unknown>) => apiClient.post(`${ED}/pre-arrival`, body),
      onSuccess: invalidate,
    }),
    /** Converts a notification into an ED visit. The patient is now here. */
    arrive: useMutation({
      mutationFn: (body: Record<string, unknown>) => apiClient.post(`${ED}/arrival`, body),
      onSuccess: invalidate,
    }),
  };
}

// ── SOS call-taker intake + triage (WU5) ──────────────────────────────────────

export type SosRequest = Record<string, unknown> & {
  id?: string;
  requestReference?: string;
  status?: string;
  incidentId?: string;
};

/** Call-taker pending queue — live incoming SOS requests (default RECEIVED). */
export function useDaidzaiRequests(status: string = "RECEIVED") {
  return useQuery({
    queryKey: ["daidzai-requests", status],
    queryFn: async () => {
      const res = await apiClient.get<unknown>(`${DZ}/requests${status ? `?status=${encodeURIComponent(status)}` : ""}`);
      return Array.isArray(res) ? (res as SosRequest[]) : [];
    },
    refetchInterval: 15_000,
  });
}

export function useDaidzaiRequest(requestId?: string) {
  return useQuery({
    queryKey: ["daidzai-request", requestId],
    queryFn: async () => apiClient.get<SosRequest>(`${DZ}/requests/${requestId}`),
    enabled: !!requestId,
    retry: false,
  });
}

export function useCreateSosRequest() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post<SosRequest>(`${DZ}/requests`, body),
  });
}

/** Triage a received SOS request → mints the incident (+ trauma episode for trauma). */
export function useTriageRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) => apiClient.post<Record<string, unknown>>(`${DZ}/requests/${requestId}/triage`, {}),
    onSuccess: (_data, requestId) => {
      void qc.invalidateQueries({ queryKey: ["daidzai-request", requestId] });
      void qc.invalidateQueries({ queryKey: ["daidzai-requests"] });
      void qc.invalidateQueries({ queryKey: ["daidzai-incidents"] });
    },
  });
}

// ── Consolidated trauma-episode timeline (WU5) ────────────────────────────────

export type TraumaEpisode = Record<string, unknown> & {
  traumaEpisodeId?: string;
  episodeReference?: string;
  status?: string;
  currentPhase?: string;
  subjectIdentityMode?: string;
  subjectCpid?: string;
  // Continuum-link status (CC-5 V-3): whether the prehospital spine has resolved to a PCT journey.
  episodeClass?: string;
  continuumLinked?: boolean;
  pctJourneyId?: string;
  continuumLinkedAt?: string;
  timeline?: Array<Record<string, unknown>>;
};

/** The prehospital phases where no PCT anchor is expected yet — the window the spine exists to cover. */
const PREHOSPITAL_PHASES = new Set(["INCIDENT", "DISPATCH", "PREHOSPITAL"]);

/**
 * Honest continuum-link state for display. LINKED once the spine resolves to a journey; UNANCHORED
 * when the patient has reached a facility phase but no journey is stamped (a correlation gap a human
 * must close — the same state the daidzai unanchored sweep surfaces); PREHOSPITAL when a link is not
 * expected yet. Never conflates "not linked yet, and that's fine" with "should be linked and isn't".
 */
export function continuumLinkState(ep: TraumaEpisode): "LINKED" | "UNANCHORED" | "PREHOSPITAL" {
  if (ep.continuumLinked || ep.pctJourneyId) return "LINKED";
  return PREHOSPITAL_PHASES.has(String(ep.currentPhase ?? "").toUpperCase()) ? "PREHOSPITAL" : "UNANCHORED";
}

export function useTraumaEpisode(episodeId?: string) {
  return useQuery({
    queryKey: ["trauma-episode", episodeId],
    queryFn: async () => apiClient.get<TraumaEpisode>(`${DZ}/trauma-episodes/${episodeId}`),
    enabled: !!episodeId,
    refetchInterval: 20_000,
  });
}

// ── Unknown-patient temp identity + reconcile (WU5) ────────────────────────────

const TID = "/internal/v1/trauma/identity";

export function useMintProvisionalIdentity() {
  return useMutation({
    mutationFn: (body: { estimated_sex?: string; estimated_date_of_birth?: string; descriptor?: string }) => {
      type Wrapped = { data: Record<string, unknown> };
      return apiClient.post<Wrapped>(`${TID}/provisional`, body).then((r) => r.data);
    },
  });
}

export function useReconcileTraumaIdentity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { survivor_crid: string; merged_crids: string[]; reason?: string }) => {
      type Wrapped = { data: Record<string, unknown> };
      return apiClient.post<Wrapped>(`${TID}/merge`, body).then((r) => r.data);
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["trauma-episode"] }),
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
