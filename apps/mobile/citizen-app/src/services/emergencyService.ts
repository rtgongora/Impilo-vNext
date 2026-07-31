/**
 * Emergency (Daidzai) Service — Citizen one-tap SOS + tracking.
 *
 * Authenticated citizens use Daidzai; guests use the public gateway SOS lane.
 */
import { apiClient, publicApiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";

const V1 = "/internal/v1/daidzai";
const PUBLIC_SOS = "/internal/v1/public/gateway/sos";

export interface EmergencyRequest {
  id: string;
  requestReference?: string;
  status?: string;
  emergencyCategory?: string;
  severity?: string;
  incidentId?: string;
  createdAt?: string;
  message?: string;
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
  /** Required for guest/public gateway SOS (callback before dispatch). */
  callbackNumber?: string;
  /** Override auth detection when caller knows session state. */
  authenticated?: boolean;
}

function mapPublicReceipt(body: Record<string, unknown>, fallbackRef?: string): EmergencyRequest {
  const attrs =
    body && typeof body === "object" && "data" in body && body.data && typeof body.data === "object"
      ? (body.data as Record<string, unknown>)
      : body;
  const ref = String(attrs.requestReference ?? fallbackRef ?? "");
  return {
    id: ref || `public-${Date.now()}`,
    requestReference: ref || undefined,
    message: typeof attrs.message === "string" ? attrs.message : undefined,
    emergencyCategory: typeof attrs.emergencyCategory === "string" ? attrs.emergencyCategory : undefined,
    severity: typeof attrs.severity === "string" ? attrs.severity : undefined,
  };
}

/** One-tap SOS — Daidzai for signed-in users; public gateway for guests. */
export async function createSos(input: CreateSosInput): Promise<EmergencyRequest> {
  const authenticated = input.authenticated ?? authStore.getState().isAuthenticated;

  if (!authenticated) {
    const callbackNumber = input.callbackNumber?.trim();
    if (!callbackNumber) {
      throw new Error("Enter a phone number we can call you back on.");
    }
    const res = await publicApiClient.post<Record<string, unknown>>(PUBLIC_SOS, {
      callbackNumber,
      emergencyCategory: input.emergencyCategory,
      locationDescription: input.locationDescription,
      description: input.description,
      lat: input.lat,
      lng: input.lng,
    });
    return mapPublicReceipt(res.data ?? {});
  }

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

/** PII-free status returned by the public status-by-reference lane. */
export interface PublicSosStatus {
  requestReference: string;
  status?: string;
  emergencyCategory?: string;
  severity?: string;
  updatedAt?: string;
  message?: string;
}

export async function fetchRequestByReference(reference: string): Promise<PublicSosStatus> {
  const res = await publicApiClient.get<unknown>(`${PUBLIC_SOS}/${encodeURIComponent(reference.trim())}`);
  const body = res.data as Record<string, unknown> | undefined;
  const source =
    body && typeof body === "object" && "data" in body && body.data && typeof body.data === "object"
      ? (body.data as Record<string, unknown>)
      : (body ?? {});
  const attrs =
    source && typeof source === "object" && "attributes" in source && source.attributes
      ? (source.attributes as Record<string, unknown>)
      : source;
  return {
    requestReference: String(attrs.requestReference ?? reference.trim()),
    status: typeof attrs.status === "string" ? attrs.status : undefined,
    emergencyCategory: typeof attrs.emergencyCategory === "string" ? attrs.emergencyCategory : undefined,
    severity: typeof attrs.severity === "string" ? attrs.severity : undefined,
    updatedAt: typeof attrs.updatedAt === "string" ? attrs.updatedAt : undefined,
    message: typeof attrs.message === "string" ? attrs.message : undefined,
  };
}
