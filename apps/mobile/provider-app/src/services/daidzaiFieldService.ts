/**
 * Daidzai Field Service — Provider/responder field-mission slice.
 *
 * Backend: experience-bff (/internal/v1/daidzai/*) → daidzai-service. The responder sees
 * incoming dispatches, records mission status (responding/on-scene/transporting), captures a
 * field triage, and records the clinical hand-over (PCT owns the encounter; this links to it).
 * Daidzai tracks the mission; Nhume executes it; the responder never duplicates the record.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/daidzai";

export interface FieldIncident {
  id: string;
  incidentReference?: string;
  emergencyCategory?: string;
  severity?: string;
  triageCategory?: string;
  status?: string;
  locationDescription?: string;
}

export interface MissionEvent {
  id: string;
  status?: string;
  note?: string;
  occurredAt?: string;
}

/** Incoming incident queue for the responder. */
export async function listIncidents(): Promise<FieldIncident[]> {
  const res = await apiClient.get<FieldIncident[]>(`${V1}/incidents`);
  return Array.isArray(res.data) ? res.data : [];
}

/** Accept / advance the mission by recording a status event. */
export async function recordMissionStatus(incidentId: string, status: string, note?: string): Promise<MissionEvent> {
  const res = await apiClient.post<MissionEvent>(`${V1}/incidents/${incidentId}/mission-events`, { status, note });
  return res.data;
}

/** Read the mission timeline for the incident. */
export async function fetchMissions(incidentId: string): Promise<MissionEvent[]> {
  const res = await apiClient.get<MissionEvent[]>(`${V1}/incidents/${incidentId}/missions`);
  return Array.isArray(res.data) ? res.data : [];
}

/** Record the clinical hand-over to a PCT encounter (link only — PCT/Butano own the record). */
export async function recordHandover(incidentId: string, pctEncounterRef: string): Promise<FieldIncident> {
  const res = await apiClient.post<FieldIncident>(`${V1}/incidents/${incidentId}/handoff`, { pctEncounterRef });
  return res.data;
}
