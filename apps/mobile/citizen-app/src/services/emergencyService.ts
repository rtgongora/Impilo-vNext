/**
 * Emergency (Daidzai) Service — Citizen one-tap SOS + tracking.
 *
 * Backend: experience-bff (/internal/v1/daidzai/*) → daidzai-service. Daidzai owns the
 * emergency truth; this client only raises the SOS and reads status. Life-safety, low
 * friction: a citizen/caregiver can raise an SOS and is never asked to pay first.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/daidzai";

export interface EmergencyRequest {
  id: string;
  requestReference?: string;
  status?: string;
  emergencyCategory?: string;
  severity?: string;
  incidentId?: string;
  createdAt?: string;
}

export interface MissionEvent {
  id: string;
  status?: string;
  note?: string;
  occurredAt?: string;
}

export interface CreateSosInput {
  forSelf: boolean;
  emergencyCategory: string;
  severity: string;
  description?: string;
  lat?: number;
  lng?: number;
  locationDescription?: string;
}

/** One-tap SOS — raises a real emergency request. */
export async function createSos(input: CreateSosInput): Promise<EmergencyRequest> {
  const body: Record<string, unknown> = {
    requesterType: input.forSelf ? "CITIZEN" : "CAREGIVER",
    subjectIdentityMode: input.forSelf ? "KNOWN" : "UNKNOWN",
    emergencyCategory: input.emergencyCategory,
    severity: input.severity,
    description: input.description,
    lat: input.lat,
    lng: input.lng,
    locationDescription: input.locationDescription,
    channel: "MOBILE",
  };
  const res = await apiClient.post<EmergencyRequest>(`${V1}/requests`, body);
  return res.data;
}

/** Read the live status of a raised request. */
export async function fetchRequest(id: string): Promise<EmergencyRequest> {
  const res = await apiClient.get<EmergencyRequest>(`${V1}/requests/${id}`);
  return res.data;
}

/** Read the mission timeline of the linked incident (empty until dispatch starts). */
export async function fetchMissions(incidentId: string): Promise<MissionEvent[]> {
  const res = await apiClient.get<MissionEvent[]>(`${V1}/incidents/${incidentId}/missions`);
  return Array.isArray(res.data) ? res.data : [];
}
