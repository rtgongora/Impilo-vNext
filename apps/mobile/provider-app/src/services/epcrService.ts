/**
 * Prehospital ePCR Service — responder/ambulance electronic patient care record.
 *
 * Backend: experience-bff → daidzai EMS clinical-dispatch context
 *   /internal/v1/daidzai/ems/*  (mission dispatch + state machine + ePCR capture).
 *
 * The crew captures a primary survey and a stream of timed observations (vitals / GCS /
 * interventions) bound to the EMS mission + canonical trauma_episode_id. Observations use
 * local-persist-then-sync: on a retryable network error the obs is queued offline (idempotency-keyed)
 * and replayed by the sync engine when connectivity returns, so a crew can capture without signal.
 * (Deep offline-conflict resilience — duplicate sync, conflicting edits — is W8.)
 */

import { apiClient } from "@impilo/mobile-api-client";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

const EMS = "/internal/v1/daidzai/ems";

export type EpcrObsType = "VITALS" | "GCS" | "INTERVENTION" | "MEDICATION" | "NOTE";

export interface EmsMission {
  id: string;
  missionReference?: string;
  incidentId?: string;
  traumaEpisodeId?: string | null;
  state: string;
  destinationFacilityId?: string | null;
}

export interface EpcrVitals {
  hr?: number;
  sbp?: number;
  dbp?: number;
  spo2?: number;
  rr?: number;
  temp?: number;
}

export interface EpcrObsInput {
  missionId: string;
  eventType: EpcrObsType;
  channel?: string;
  payload: Record<string, unknown>;
}

export interface EpcrObsResult {
  /** Server id when sent live, or a local-… id when queued offline. */
  id: string;
  queued: boolean;
}

export interface EpcrSnapshot {
  latestVitals?: EpcrVitals | null;
  latestGcs?: Record<string, unknown> | null;
  interventions?: unknown[];
  narrative?: string | null;
  obsCount?: number;
}

export interface EpcrView {
  epcrId: string;
  missionId: string;
  traumaEpisodeId?: string | null;
  patientHealthId?: string | null;
  narrative?: string | null;
  events: Array<{ eventType: string; channel?: string; payload?: unknown; recordedAt?: string }>;
  snapshot: EpcrSnapshot;
}

/** Some BFF passthroughs wrap the entity in { data }, others return it raw — unwrap defensively. */
function unwrap<T>(response: unknown): T {
  const r = response as { data?: unknown };
  if (r && typeof r === "object" && "data" in r && r.data && typeof r.data === "object") {
    const inner = r.data as { data?: unknown };
    return (inner.data ?? r.data) as T;
  }
  return response as T;
}

/** Resolve the EMS mission already dispatched for an incident (the crew's active mission). */
export async function getMissionForIncident(incidentId: string): Promise<EmsMission> {
  const res = await apiClient.get<unknown>(`${EMS}/incidents/${incidentId}/mission`);
  return unwrap<EmsMission>(res);
}

export async function getMission(missionId: string): Promise<EmsMission> {
  const res = await apiClient.get<unknown>(`${EMS}/missions/${missionId}`);
  return unwrap<EmsMission>(res);
}

/** Advance the mission state machine (e.g. ON_SCENE → PATIENT_CONTACT → EN_ROUTE_FACILITY). */
export async function advanceMission(
  missionId: string,
  toState: string,
  extra?: { note?: string; pctEncounterRef?: string }
): Promise<EmsMission> {
  const res = await apiClient.post<unknown>(`${EMS}/missions/${missionId}/advance`, {
    toState,
    ...extra,
  });
  return unwrap<EmsMission>(res);
}

/** Create/update the ePCR for a mission (primary survey + patient binding). */
export async function upsertEpcr(
  missionId: string,
  input: { patientHealthId?: string; primarySurvey?: Record<string, unknown>; narrative?: string }
): Promise<EpcrObsResult> {
  const path = `${EMS}/missions/${missionId}/epcr`;
  try {
    const res = await apiClient.post<{ id?: string }>(path, input);
    const body = unwrap<{ id?: string }>(res);
    return { id: body.id ?? `epcr-${missionId}`, queued: false };
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "ems_epcr",
      path,
      payload: input as Record<string, unknown>,
      recordId: `local-epcr-${missionId}`,
      operationType: "UPDATE",
    });
    if (offline) return { id: offline.recordId, queued: true };
    throw error;
  }
}

/**
 * Append a timed prehospital observation. Sent live when online; queued offline (and replayed with
 * an idempotency key) on a retryable failure so the crew never loses an observation.
 */
export async function recordObs(input: EpcrObsInput): Promise<EpcrObsResult> {
  const path = `${EMS}/missions/${input.missionId}/epcr/events`;
  const payload: Record<string, unknown> = {
    eventType: input.eventType,
    channel: input.channel ?? "OBS",
    payload: input.payload,
  };
  try {
    const res = await apiClient.post<{ id?: string }>(path, payload);
    const body = unwrap<{ id?: string }>(res);
    return { id: body.id ?? `obs-${Date.now()}`, queued: false };
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "ems_epcr_event",
      path,
      payload,
    });
    if (offline) return { id: offline.recordId, queued: true };
    throw error;
  }
}

export async function getEpcr(missionId: string): Promise<EpcrView> {
  const res = await apiClient.get<unknown>(`${EMS}/missions/${missionId}/epcr`);
  return unwrap<EpcrView>(res);
}
