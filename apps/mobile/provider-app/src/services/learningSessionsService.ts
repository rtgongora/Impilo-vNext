/**
 * Learning Sessions Service — scheduled Fundo learning sessions (LEARNING_LIVE)
 * for the provider (facilitator-capable) app.
 *
 * Backend:
 * - Sessions: experience-bff /internal/v1/learning/v11/sessions[/{sessionId}]
 *   (W3 contract adds sessionMode, liveEventId, joinPath; older rows carry the
 *   same facts inside metadata.impiloLiveEventId / impiloLiveJoinPath).
 * - Attendance: GET /internal/v1/learning/v11/sessions/{sessionId}/attendance
 *   → { data: { items: [...] } } (facilitator affordance).
 * - Media join: shared Impilo Live room flow —
 *   POST /internal/v1/live/room/{liveEventId}/join   (registers the classroom
 *   session + records attendance for the participant), then
 *   POST /internal/v1/live/room/{liveEventId}/token  → { roomId, roomUrl,
 *   accessToken, provider, channel } wrapped in a { data } envelope.
 *   The token route fails until join has run ("No active session — join first"),
 *   so joinLiveClassroom always performs join → token.
 */

import { apiClient } from "@impilo/mobile-api-client";
import { authStore } from "@impilo/mobile-auth";

type AnyRecord = Record<string, unknown>;

const V11 = "/internal/v1/learning/v11";
const LIVE_ROOM = "/internal/v1/live/room";

export type LearningSessionMode = "LIVE" | "SCHEDULED" | string;

export interface LearningSession {
  id: string;
  courseId?: string | null;
  cohortId?: string | null;
  title: string;
  sessionType: string;
  /** W3 contract field; derived from live metadata for older rows. */
  sessionMode: LearningSessionMode;
  startsAt?: string | null;
  endsAt?: string | null;
  facilitator?: string | null;
  locationRef?: string | null;
  /** Impilo Live event backing this session (media join anchor). */
  liveEventId?: string | null;
  /** Experience join path hint from the BFF (e.g. /live/event/{id}). */
  joinPath?: string | null;
  /** Optional objectives carried in session metadata. */
  objectives: string[];
}

export interface ClassroomMediaCredentials {
  serverUrl: string;
  token: string;
  roomId?: string;
  provider?: string;
  channel?: string;
}

export interface SessionAttendanceSummary {
  count: number;
  items: AnyRecord[];
}

interface BffEnvelope<T> {
  data?: T;
}

function asRecord(value: unknown): AnyRecord {
  return value && typeof value === "object" ? (value as AnyRecord) : {};
}

function asOptionalString(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  return String(value);
}

/** Normalize a BFF session row into the W3 LEARNING_LIVE contract shape. */
export function normalizeLearningSession(raw: AnyRecord): LearningSession {
  const metadata = asRecord(raw.metadata);
  const liveEventId =
    asOptionalString(raw.liveEventId ?? raw.live_event_id) ??
    asOptionalString(metadata.impiloLiveEventId ?? metadata.impilo_live_event_id);
  const joinPath =
    asOptionalString(raw.joinPath ?? raw.join_path) ??
    asOptionalString(metadata.impiloLiveJoinPath ?? metadata.impilo_live_join_path);
  const declaredMode = asOptionalString(raw.sessionMode ?? raw.session_mode);
  const objectivesRaw = metadata.objectives ?? raw.objectives;
  const objectives = Array.isArray(objectivesRaw) ? objectivesRaw.map((o) => String(o)) : [];

  return {
    id: String(raw.id ?? ""),
    courseId: asOptionalString(raw.courseId ?? raw.course_id),
    cohortId: asOptionalString(raw.cohortId ?? raw.cohort_id),
    title: String(raw.title ?? "Learning session"),
    sessionType: String(raw.sessionType ?? raw.session_type ?? "VIRTUAL"),
    // W3 field when present; otherwise a live-event anchor means LIVE.
    sessionMode: declaredMode ?? (liveEventId ? "LIVE" : "SCHEDULED"),
    startsAt: asOptionalString(raw.startsAt ?? raw.starts_at),
    endsAt: asOptionalString(raw.endsAt ?? raw.ends_at),
    facilitator: asOptionalString(raw.facilitator),
    locationRef: asOptionalString(raw.locationRef ?? raw.location_ref),
    liveEventId,
    joinPath,
    objectives,
  };
}

/** A session is joinable as a live classroom when LIVE-mode and anchored to a live event. */
export function isJoinableLiveSession(session: LearningSession): boolean {
  return session.sessionMode === "LIVE" && Boolean(session.liveEventId);
}

export async function listSessions(limit = 50): Promise<LearningSession[]> {
  const r = await apiClient.get<BffEnvelope<{ items?: AnyRecord[] }>>(`${V11}/sessions?limit=${limit}`);
  const items = r.data.data?.items ?? [];
  return items
    .filter((item) => item && typeof item === "object")
    .map((item) => normalizeLearningSession(item))
    .filter((s) => Boolean(s.id));
}

export async function getSession(sessionId: string): Promise<LearningSession | null> {
  try {
    const r = await apiClient.get<BffEnvelope<AnyRecord>>(
      `${V11}/sessions/${encodeURIComponent(sessionId)}`,
    );
    const payload = r.data.data ?? {};
    const row = asRecord((payload as AnyRecord).session ?? payload);
    if (row.id) return normalizeLearningSession(row);
  } catch {
    // The per-session route lands with the W3 backend wave; fall back to the list.
  }
  const sessions = await listSessions();
  return sessions.find((s) => s.id === sessionId) ?? null;
}

/** Facilitator affordance: attendance recorded against the learning session. */
export async function fetchSessionAttendance(sessionId: string): Promise<SessionAttendanceSummary> {
  const r = await apiClient.get<BffEnvelope<{ items?: AnyRecord[] }>>(
    `${V11}/sessions/${encodeURIComponent(sessionId)}/attendance`,
  );
  const items = (r.data.data?.items ?? []).filter((item) => item && typeof item === "object");
  return { count: items.length, items };
}

/** Normalize the live room-token envelope (shape parity with W2's media-token normalizer). */
export function normalizeClassroomMediaPayload(
  payload: BffEnvelope<AnyRecord> | AnyRecord | null | undefined,
): ClassroomMediaCredentials | null {
  const row = asRecord((payload as BffEnvelope<AnyRecord> | undefined)?.data ?? payload);
  const serverUrl = asOptionalString(row.roomUrl ?? row.room_url ?? row.serverUrl ?? row.server_url);
  const token = asOptionalString(row.accessToken ?? row.access_token ?? row.token);
  if (!serverUrl || !token) return null;
  return {
    serverUrl,
    token,
    roomId: asOptionalString(row.roomId ?? row.room_id) ?? undefined,
    provider: asOptionalString(row.provider) ?? undefined,
    channel: asOptionalString(row.channel) ?? undefined,
  };
}

function participantId(): string {
  return authStore.getState().session?.actorId ?? "";
}

/**
 * Join a live classroom: room join (registers the media session and records
 * attendance for this participant) then a scoped room token. Providers join
 * as PRESENTER when facilitating, otherwise ATTENDEE.
 */
export async function joinLiveClassroom(
  liveEventId: string,
  options: { asFacilitator?: boolean } = {},
): Promise<ClassroomMediaCredentials> {
  const actor = participantId();
  const role = options.asFacilitator ? "PRESENTER" : "ATTENDEE";
  const base = `${LIVE_ROOM}/${encodeURIComponent(liveEventId)}`;
  await apiClient.post(`${base}/join`, {
    participantId: actor,
    participantType: "PROVIDER",
    role,
    consentGranted: true,
  });
  const tokenResponse = await apiClient.post<BffEnvelope<AnyRecord>>(`${base}/token`, {
    participantId: actor,
    role,
  });
  const credentials = normalizeClassroomMediaPayload(tokenResponse.data);
  if (!credentials) {
    throw new Error("Governed classroom media is not ready yet. Please try again shortly.");
  }
  return credentials;
}
