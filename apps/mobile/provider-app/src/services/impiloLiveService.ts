/**
 * Impilo Live — provider mobile API client.
 * BFF: /internal/v1/mobile/provider/live/**
 */

import { apiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";

const BASE = "/internal/v1/mobile/provider/live";
const LIVE_BASE = "/internal/v1/live";

export type LiveEventStatus = "DRAFT" | "SCHEDULED" | "LIVE" | "ENDED" | "CANCELLED" | string;

export interface LiveEvent {
  id: string;
  title: string;
  description?: string | null;
  eventType: string;
  contextType?: string;
  status: LiveEventStatus;
  facilityId?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  cpdEnabled?: boolean;
  cpdPoints?: number | string | null;
}

export interface LiveRegistration {
  id: string;
  eventId: string;
  participantId: string;
  registrationStatus: string;
  registeredAt?: string;
}

export interface LiveCertificate {
  id: string;
  eventId: string;
  participantId: string;
  certificateType: string;
  verificationCode: string;
  issuedAt?: string;
}

export interface LiveAttendance {
  eventId: string;
  participantId: string;
  liveWatchMinutes?: number;
  totalWatchMinutes?: number;
  eligibleForCpd?: boolean;
  eligibleForCertificate?: boolean;
  completionStatus?: string;
}

export interface LiveRoomSession {
  sessionId?: string;
  eventId?: string;
  roomUrl?: string;
  healthStatus?: string;
}

interface BffEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

function unwrap<T>(body: BffEnvelope<T> | T): T {
  if (body && typeof body === "object" && "data" in (body as BffEnvelope<T>)) {
    return (body as BffEnvelope<T>).data;
  }
  return body as T;
}

function normalizeEvent(raw: Record<string, unknown>): LiveEvent {
  return {
    id: String(raw.id ?? ""),
    title: String(raw.title ?? "Live event"),
    description: (raw.description as string | null | undefined) ?? null,
    eventType: String(raw.eventType ?? raw.event_type ?? "WEBINAR"),
    contextType: String(raw.contextType ?? raw.context_type ?? "PROFESSIONAL"),
    status: String(raw.status ?? "SCHEDULED") as LiveEventStatus,
    facilityId: (raw.facilityId ?? raw.facility_id ?? null) as string | null,
    startTime: (raw.startTime ?? raw.start_time ?? null) as string | null,
    endTime: (raw.endTime ?? raw.end_time ?? null) as string | null,
    cpdEnabled: Boolean(raw.cpdEnabled ?? raw.cpd_enabled ?? false),
    cpdPoints: (raw.cpdPoints ?? raw.cpd_points ?? null) as number | string | null,
  };
}

function normalizeRegistration(raw: Record<string, unknown>): LiveRegistration {
  return {
    id: String(raw.id ?? ""),
    eventId: String(raw.eventId ?? raw.event_id ?? ""),
    participantId: String(raw.participantId ?? raw.participant_id ?? ""),
    registrationStatus: String(raw.registrationStatus ?? raw.registration_status ?? "REGISTERED"),
    registeredAt: (raw.registeredAt ?? raw.registered_at) as string | undefined,
  };
}

function normalizeCertificate(raw: Record<string, unknown>): LiveCertificate {
  return {
    id: String(raw.id ?? ""),
    eventId: String(raw.eventId ?? raw.event_id ?? ""),
    participantId: String(raw.participantId ?? raw.participant_id ?? ""),
    certificateType: String(raw.certificateType ?? raw.certificate_type ?? "ATTENDANCE"),
    verificationCode: String(raw.verificationCode ?? raw.verification_code ?? ""),
    issuedAt: (raw.issuedAt ?? raw.issued_at) as string | undefined,
  };
}

function asEventList(data: unknown): LiveEvent[] {
  const list = Array.isArray(data) ? data : [];
  return list
    .filter((item) => item && typeof item === "object")
    .map((item) => normalizeEvent(item as Record<string, unknown>))
    .filter((e) => Boolean(e.id));
}

function asRegistrationList(data: unknown): LiveRegistration[] {
  const list = Array.isArray(data) ? data : [];
  return list
    .filter((item) => item && typeof item === "object")
    .map((item) => normalizeRegistration(item as Record<string, unknown>));
}

function asCertificateList(data: unknown): LiveCertificate[] {
  const list = Array.isArray(data) ? data : [];
  return list
    .filter((item) => item && typeof item === "object")
    .map((item) => normalizeCertificate(item as Record<string, unknown>));
}

function actorId(): string {
  return authStore.getState().session?.actorId ?? "";
}

function facilityId(): string | undefined {
  return authStore.getState().session?.facilityId ?? undefined;
}

/** Discover professional live events (facility-scoped when available). */
export async function discoverEvents(options: { contextType?: string; role?: string } = {}): Promise<LiveEvent[]> {
  const params = new URLSearchParams();
  params.set("context_type", options.contextType ?? "PROFESSIONAL");
  params.set("role", options.role ?? "PROVIDER");
  const fac = facilityId();
  if (fac) params.set("facility_id", fac);
  try {
    const response = await apiClient.get<BffEnvelope<unknown>>(`${BASE}/discover?${params.toString()}`);
    return asEventList(unwrap(response.data));
  } catch {
    return [];
  }
}

/** Register the signed-in provider for an event. */
export async function register(eventId: string): Promise<LiveRegistration | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/register`,
      { inviteSource: "MOBILE_PROVIDER_HUB" },
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? normalizeRegistration(data) : null;
  } catch {
    return null;
  }
}

/** Join a live event room as attendee. */
export async function join(eventId: string): Promise<LiveRoomSession | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/join`,
      { role: "ATTENDEE" },
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? (data as LiveRoomSession) : null;
  } catch {
    return null;
  }
}

/** List certificates issued for an event. */
export async function listCertificates(eventId: string): Promise<LiveCertificate[]> {
  try {
    const response = await apiClient.get<BffEnvelope<unknown>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/certificates`,
    );
    return asCertificateList(unwrap(response.data));
  } catch {
    return [];
  }
}

/** CPD-eligible ended events plus attendance for the signed-in provider. */
export async function listCpdHistory(): Promise<Array<LiveEvent & { attendance?: LiveAttendance | null }>> {
  try {
    const [endedRes, registrations] = await Promise.all([
      apiClient.get<BffEnvelope<unknown>>(`${LIVE_BASE}/events?status=ENDED`),
      listMyRegistrations(),
    ]);
    const ended = asEventList(unwrap(endedRes.data)).filter((e) => e.cpdEnabled);
    const participantId = actorId();
    const history = await Promise.all(
      ended.map(async (event) => {
        let attendance: LiveAttendance | null = null;
        if (participantId) {
          try {
            const attRes = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
              `${BASE}/events/${encodeURIComponent(event.id)}/attendance`,
            );
            const raw = unwrap(attRes.data);
            if (raw && typeof raw === "object") {
              attendance = {
                eventId: event.id,
                participantId,
                liveWatchMinutes: Number((raw as Record<string, unknown>).liveWatchMinutes ?? 0),
                totalWatchMinutes: Number((raw as Record<string, unknown>).totalWatchMinutes ?? 0),
                eligibleForCpd: Boolean((raw as Record<string, unknown>).eligibleForCpd),
                eligibleForCertificate: Boolean((raw as Record<string, unknown>).eligibleForCertificate),
                completionStatus: String((raw as Record<string, unknown>).completionStatus ?? ""),
              };
            }
          } catch {
            attendance = null;
          }
        }
        const registered = registrations.some((r) => r.eventId === event.id);
        if (!registered && !attendance?.eligibleForCpd) return null;
        return { ...event, attendance };
      }),
    );
    return history.filter((row) => row !== null) as Array<LiveEvent & { attendance?: LiveAttendance | null }>;
  } catch {
    return [];
  }
}

/** List registrations for the signed-in provider. */
export async function listMyRegistrations(): Promise<LiveRegistration[]> {
  const participantId = actorId();
  if (!participantId) return [];
  try {
    const response = await apiClient.get<BffEnvelope<unknown>>(
      `${LIVE_BASE}/registrations/participants/${encodeURIComponent(participantId)}`,
    );
    return asRegistrationList(unwrap(response.data));
  } catch {
    return [];
  }
}

/** Host: start a scheduled event (go live). */
export async function startEvent(eventId: string): Promise<LiveRoomSession | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/start`,
      {},
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? (data as LiveRoomSession) : null;
  } catch {
    return null;
  }
}
