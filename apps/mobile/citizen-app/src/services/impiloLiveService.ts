/**
 * Impilo Live — citizen mobile API client.
 * BFF: /internal/v1/mobile/citizen/live/**
 */

import { apiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";

const BASE = "/internal/v1/mobile/citizen/live";
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
  qnaEnabled?: boolean;
  linkedMadiDriveId?: string | null;
}

export interface LiveRegistration {
  id: string;
  eventId: string;
  participantId: string;
  registrationStatus: string;
  saved?: boolean;
  registeredAt?: string;
}

export interface LiveRoomSession {
  sessionId?: string;
  eventId?: string;
  roomUrl?: string;
  accessToken?: string;
  provider?: string;
  channel?: string;
  healthStatus?: string;
}

export interface LiveQuestion {
  id?: string;
  eventId?: string;
  questionText?: string;
  status?: string;
}

export interface LiveFeedbackInput {
  rating?: number;
  comments?: string;
  helpful?: boolean;
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
    contextType: String(raw.contextType ?? raw.context_type ?? "CITIZEN"),
    status: String(raw.status ?? "SCHEDULED") as LiveEventStatus,
    facilityId: (raw.facilityId ?? raw.facility_id ?? null) as string | null,
    startTime: (raw.startTime ?? raw.start_time ?? null) as string | null,
    endTime: (raw.endTime ?? raw.end_time ?? null) as string | null,
    cpdEnabled: Boolean(raw.cpdEnabled ?? raw.cpd_enabled ?? false),
    cpdPoints: (raw.cpdPoints ?? raw.cpd_points ?? null) as number | string | null,
    qnaEnabled: Boolean(raw.qnaEnabled ?? raw.qna_enabled ?? true),
    linkedMadiDriveId: (raw.linkedMadiDriveId ?? raw.linked_madi_drive_id ?? null) as string | null,
  };
}

function normalizeRegistration(raw: Record<string, unknown>): LiveRegistration {
  return {
    id: String(raw.id ?? ""),
    eventId: String(raw.eventId ?? raw.event_id ?? ""),
    participantId: String(raw.participantId ?? raw.participant_id ?? ""),
    registrationStatus: String(raw.registrationStatus ?? raw.registration_status ?? "REGISTERED"),
    saved: Boolean(raw.saved ?? false),
    registeredAt: (raw.registeredAt ?? raw.registered_at) as string | undefined,
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

function actorId(): string {
  return authStore.getState().session?.actorId ?? "";
}

/** Discover upcoming and live citizen events. */
export async function discoverEvents(contextType = "CITIZEN"): Promise<LiveEvent[]> {
  try {
    const response = await apiClient.get<BffEnvelope<unknown>>(
      `${BASE}/discover?context_type=${encodeURIComponent(contextType)}`,
    );
    return asEventList(unwrap(response.data));
  } catch {
    return [];
  }
}

/** Register the signed-in citizen for an event. */
export async function registerForEvent(
  eventId: string,
  options: { inviteSource?: string } = {},
): Promise<LiveRegistration | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/register`,
      { inviteSource: options.inviteSource ?? "MOBILE_DISCOVER" },
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? normalizeRegistration(data) : null;
  } catch {
    return null;
  }
}

/** Join a live event room (returns session / media token envelope). */
export async function joinEvent(eventId: string): Promise<LiveRoomSession | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/join`,
      { role: "ATTENDEE" },
    );
    const data = unwrap(response.data);
    if (!data || typeof data !== "object") return null;
    return data as LiveRoomSession;
  } catch {
    return null;
  }
}

/** Submit a Q&A question during a live event. */
export async function submitQuestion(eventId: string, questionText: string): Promise<LiveQuestion | null> {
  try {
    const response = await apiClient.post<BffEnvelope<Record<string, unknown>>>(
      `${BASE}/events/${encodeURIComponent(eventId)}/questions`,
      { questionText, anonymousAllowed: false },
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? (data as LiveQuestion) : null;
  } catch {
    return null;
  }
}

/** Submit post-event feedback. */
export async function submitFeedback(eventId: string, input: LiveFeedbackInput): Promise<boolean> {
  try {
    await apiClient.post(`${BASE}/events/${encodeURIComponent(eventId)}/feedback`, input);
    return true;
  } catch {
    return false;
  }
}

/** List registrations for the signed-in citizen. */
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

/** List ended events available for replay. */
export async function listReplays(): Promise<LiveEvent[]> {
  try {
    const response = await apiClient.get<BffEnvelope<unknown>>(`${LIVE_BASE}/events?status=ENDED`);
    return asEventList(unwrap(response.data));
  } catch {
    return [];
  }
}

/** Fetch a single event by id (web BFF surface). */
export async function fetchEvent(eventId: string): Promise<LiveEvent | null> {
  try {
    const response = await apiClient.get<BffEnvelope<Record<string, unknown>>>(
      `${LIVE_BASE}/events/${encodeURIComponent(eventId)}`,
    );
    const data = unwrap(response.data);
    return data && typeof data === "object" ? normalizeEvent(data) : null;
  } catch {
    return null;
  }
}

export function isDonorMobilisationEvent(event: LiveEvent): boolean {
  return (
    event.eventType === "DONOR_MOBILISATION" ||
    Boolean(event.linkedMadiDriveId)
  );
}
