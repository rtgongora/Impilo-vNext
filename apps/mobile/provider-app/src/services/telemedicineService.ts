import { apiClient } from "@impilo/mobile-api-client";
import type { TelemedicineSession } from "../types";

const V1 = "/internal/v1/mobile/provider/telemedicine";

type SessionEnvelopeRow = {
  id?: string;
  status?: string;
  patientId?: string;
  patient_id?: string;
  patientCpid?: string;
  providerId?: string;
  provider_id?: string;
  encounterId?: string;
  encounter_id?: string;
  referralId?: string;
  referral_id?: string;
  scheduledAt?: string;
  scheduled_at?: string;
  startedAt?: string;
  started_at?: string;
  endedAt?: string;
  ended_at?: string;
  channel?: string;
  channel_id?: string;
  channelId?: string;
  token?: string;
  accessToken?: string;
  access_token?: string;
  session_token?: string;
  sessionToken?: string;
  roomUrl?: string;
  room_url?: string;
  mediaStatus?: string;
  media_status?: string;
  attributes?: Record<string, unknown>;
};

export function mapTelemedicineSession(row: SessionEnvelopeRow): TelemedicineSession {
  const attr = (row.attributes ?? {}) as Record<string, unknown>;
  return {
    id: String(row.id ?? attr.id ?? ""),
    encounterId: String(row.encounterId ?? row.encounter_id ?? attr.encounter_id ?? attr.encounterId ?? ""),
    patientId: String(row.patientId ?? row.patient_id ?? row.patientCpid ?? attr.patient_id ?? attr.patientId ?? attr.patientCpid ?? ""),
    providerId: String(row.providerId ?? row.provider_id ?? attr.provider_id ?? attr.providerId ?? ""),
    status: String(row.status ?? attr.status ?? "SCHEDULED") as TelemedicineSession["status"],
    scheduledAt: String(row.scheduledAt ?? row.scheduled_at ?? attr.scheduled_at ?? attr.scheduledAt ?? new Date().toISOString()),
    startedAt: value(row.startedAt ?? row.started_at ?? attr.started_at ?? attr.startedAt),
    endedAt: value(row.endedAt ?? row.ended_at ?? attr.ended_at ?? attr.endedAt),
    sessionToken: value(row.sessionToken ?? row.session_token ?? row.accessToken ?? row.access_token ?? row.token ?? attr.session_token ?? attr.accessToken ?? attr.token),
    channelId: value(row.channelId ?? row.channel_id ?? row.channel ?? attr.channel_id ?? attr.channelId ?? attr.channel),
    roomUrl: value(row.roomUrl ?? row.room_url ?? attr.roomUrl ?? attr.room_url),
    mediaStatus: normalizeMediaStatus(value(row.mediaStatus ?? row.media_status ?? attr.mediaStatus ?? attr.media_status)),
  };
}

export async function listProviderTelemedicineSessions(params: {
  facilityId?: string | null;
  providerId?: string | null;
  page?: number;
  size?: number;
}): Promise<TelemedicineSession[]> {
  const qp = new URLSearchParams();
  if (params.facilityId) qp.set("facility_id", params.facilityId);
  if (params.providerId) qp.set("provider_id", params.providerId);
  qp.set("page", String(params.page ?? 0));
  qp.set("size", String(params.size ?? 50));
  const response = await apiClient.get<{ data: SessionEnvelopeRow[] | { items?: SessionEnvelopeRow[]; data?: SessionEnvelopeRow[] } }>(
    `${V1}/sessions?${qp.toString()}`
  );
  const root = response.data.data;
  const rows = Array.isArray(root) ? root : Array.isArray(root.items) ? root.items : Array.isArray(root.data) ? root.data : [];
  return rows.map(mapTelemedicineSession);
}

export async function joinProviderTelemedicineSession(id: string): Promise<TelemedicineSession> {
  const response = await apiClient.post<{ data: SessionEnvelopeRow }>(`${V1}/sessions/${encodeURIComponent(id)}/join`);
  return mapTelemedicineSession(response.data.data);
}

export async function endProviderTelemedicineSession(id: string, notes?: string): Promise<void> {
  await apiClient.post(`${V1}/sessions/${encodeURIComponent(id)}/end`, notes ? { notes } : undefined);
}

export async function sendProviderTelemedicineSignal(
  id: string,
  kind: "delay" | "nudge" | "support"
): Promise<void> {
  const message =
    kind === "delay"
      ? "Provider delay notice: running late but will join shortly."
      : kind === "nudge"
        ? "No-show nudge: please join your scheduled teleconsult."
        : "Support requested for teleconsult session.";
  await apiClient.post(`/internal/v1/teleconsult/sessions/${encodeURIComponent(id)}/messages`, {
    message,
    kind: kind.toUpperCase(),
  });
}

/* ── Waiting room + governed RTC media token (shared teleconsult contract) ──
 *
 * The mobile provider BFF surface (/internal/v1/mobile/provider/telemedicine/*)
 * only exposes list/create/join/end — it has no waiting-room, admit/deny, or
 * media-token routes. Those live on the shared /internal/v1/teleconsult/*
 * contract (same one the web shell uses), so the app calls them directly —
 * the api client injects the same trust headers either way.
 */

const TELECONSULT_V1 = "/internal/v1/teleconsult";

export interface WaitingRoomParticipant {
  identity: string;
  displayName?: string;
  waitingSince?: string;
}

type WaitingRoomRow = {
  identity?: string;
  participantId?: string;
  participant_id?: string;
  id?: string;
  displayName?: string;
  display_name?: string;
  name?: string;
  waitingSince?: string;
  waiting_since?: string;
  requestedAt?: string;
  requested_at?: string;
  joinedAt?: string;
  joined_at?: string;
};

type WaitingRoomEnvelope = WaitingRoomRow[] | {
  waiting?: WaitingRoomRow[];
  participants?: WaitingRoomRow[];
  items?: WaitingRoomRow[];
};

export async function fetchTelemedicineWaitingRoom(sessionId: string): Promise<WaitingRoomParticipant[]> {
  const response = await apiClient.get<{ data?: WaitingRoomEnvelope }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/waiting-room`
  );
  const root = (response.data?.data ?? response.data ?? []) as WaitingRoomEnvelope;
  const list = root as { waiting?: WaitingRoomRow[]; participants?: WaitingRoomRow[]; items?: WaitingRoomRow[] };
  const rows: WaitingRoomRow[] = Array.isArray(root)
    ? root
    : Array.isArray(list.waiting)
      ? list.waiting
      : Array.isArray(list.participants)
        ? list.participants
        : Array.isArray(list.items)
          ? list.items
          : [];
  return rows
    .map((row) => ({
      identity: String(row.identity ?? row.participantId ?? row.participant_id ?? row.id ?? ""),
      displayName: value(row.displayName ?? row.display_name ?? row.name),
      waitingSince: value(
        row.waitingSince ?? row.waiting_since ?? row.requestedAt ?? row.requested_at ?? row.joinedAt ?? row.joined_at
      ),
    }))
    .filter((participant) => participant.identity.length > 0);
}

/** TM-B18: pool context for the waiting room (queue depth / oldest waiting / estimated wait). */
export interface WaitingRoomPoolContext {
  poolId?: string;
  depth?: number;
  oldestWaitingMinutes?: number;
  estimatedWaitMinutes?: number;
}

export async function fetchTelemedicineWaitingRoomPoolContext(
  sessionId: string
): Promise<WaitingRoomPoolContext | null> {
  const response = await apiClient.get<{ data?: { poolContext?: WaitingRoomPoolContext | null } }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/waiting-room`
  );
  const root = (response.data?.data ?? response.data ?? {}) as { poolContext?: WaitingRoomPoolContext | null };
  return root.poolContext ?? null;
}

/* ── TM-B18 parity: guarded lifecycle + downgrade ladder + provider notes + tasks ──
 * Mirrors the web console's teleconsult surface; every action is server-guarded
 * (illegal transitions 409, dissent/reason requirements enforced in pct). */

export interface AllowedActionsResult {
  allowedActions: string[];
}

export async function fetchTeleconsultAllowedActions(sessionId: string): Promise<string[]> {
  const response = await apiClient.get<{ data?: { allowedActions?: string[]; allowed_actions?: string[] } }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/allowed-actions`
  );
  const root = (response.data?.data ?? {}) as { allowedActions?: string[]; allowed_actions?: string[] };
  return root.allowedActions ?? root.allowed_actions ?? [];
}

/** Reason-bound guarded lifecycle transition (cancel/reopen/escalate/transfer/error-mark). */
export async function performTeleconsultLifecycleAction(
  sessionId: string,
  action: "cancel" | "reopen" | "escalate" | "transfer" | "error-mark",
  reason: string
): Promise<void> {
  await apiClient.post(`${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/${action}`, { reason });
}

/** TM-B18 (epic-named): native media downgrade ladder VIDEO → AUDIO → ASYNC; restore=true to step up. */
export async function changeTeleconsultMediaModality(
  sessionId: string,
  modality: "VIDEO" | "AUDIO" | "ASYNC",
  options?: { reason?: string; restore?: boolean }
): Promise<void> {
  await apiClient.post(`${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/media-downgrade`, {
    modality,
    reason: options?.reason,
    restore: options?.restore ?? false,
  });
}

export interface ProviderNote {
  author?: string;
  authorName?: string;
  note?: string;
  timestamp?: string;
}

/** Provider-only side-channel — never patient-visible (structural boundary in pct). */
export async function fetchTeleconsultProviderNotes(sessionId: string): Promise<ProviderNote[]> {
  const response = await apiClient.get<{ data?: { providerNotes?: ProviderNote[] } }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/provider-notes`
  );
  return (response.data?.data ?? {}).providerNotes ?? [];
}

export async function addTeleconsultProviderNote(sessionId: string, note: string): Promise<void> {
  await apiClient.post(`${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/provider-note`, { note });
}

export interface TeleconsultTask {
  taskId?: string;
  title?: string;
  status?: string;
  blocksClosure?: boolean;
}

export async function fetchTeleconsultTasks(sessionId: string): Promise<TeleconsultTask[]> {
  const response = await apiClient.get<{ data?: { tasks?: TeleconsultTask[] } | TeleconsultTask[] }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/tasks`
  );
  const root = response.data?.data ?? [];
  if (Array.isArray(root)) return root;
  return root.tasks ?? [];
}

export async function admitTelemedicineParticipant(sessionId: string, identity: string): Promise<void> {
  await apiClient.post(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/admit`,
    { identity }
  );
}

export async function denyTelemedicineParticipant(
  sessionId: string,
  identity: string,
  reason?: string
): Promise<void> {
  await apiClient.post(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/deny`,
    { identity, ...(reason ? { reason } : {}) }
  );
}

export type MediaTokenStatus = "READY" | "WAITING" | "DENIED";

export interface MediaTokenResult {
  status: MediaTokenStatus;
  roomUrl?: string;
  token?: string;
  reason?: string;
}

export interface ProviderMediaTokenRequest {
  displayName: string;
  mediaProfile?: "AUDIO_ONLY" | "AUDIO_VIDEO";
}

type MediaTokenRow = {
  status?: string;
  room_url?: string;
  roomUrl?: string;
  token?: string;
  accessToken?: string;
  access_token?: string;
  reason?: string;
  denied_reason?: string;
};

export function normalizeMediaTokenPayload(payload: { data?: MediaTokenRow } | MediaTokenRow | null | undefined): MediaTokenResult {
  const row: MediaTokenRow = ((payload as { data?: MediaTokenRow } | undefined)?.data ?? payload ?? {}) as MediaTokenRow;
  const status = String(row.status ?? "").toUpperCase();
  if (status === "DENIED") {
    return { status: "DENIED", reason: row.reason ?? row.denied_reason };
  }
  if (status === "WAITING") {
    return { status: "WAITING" };
  }
  const roomUrl = row.room_url ?? row.roomUrl;
  const token = row.token ?? row.accessToken ?? row.access_token;
  if (roomUrl && token) {
    return { status: "READY", roomUrl, token };
  }
  return { status: "WAITING" };
}

function providerMediaTokenBody(request: ProviderMediaTokenRequest): Record<string, unknown> {
  return {
    displayName: request.displayName,
    role: "PROVIDER",
    ...(request.mediaProfile ? { mediaProfile: request.mediaProfile } : {}),
  };
}

export async function requestProviderTelemedicineMediaToken(
  sessionId: string,
  request: ProviderMediaTokenRequest
): Promise<MediaTokenResult> {
  const response = await apiClient.post<{ data?: MediaTokenRow }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/media/token`,
    providerMediaTokenBody(request)
  );
  return normalizeMediaTokenPayload(response.data);
}

export async function refreshProviderTelemedicineMediaToken(
  sessionId: string,
  request: ProviderMediaTokenRequest
): Promise<MediaTokenResult> {
  const response = await apiClient.post<{ data?: MediaTokenRow }>(
    `${TELECONSULT_V1}/sessions/${encodeURIComponent(sessionId)}/media/token/refresh`,
    providerMediaTokenBody(request)
  );
  return normalizeMediaTokenPayload(response.data);
}

function value(input: unknown): string | undefined {
  if (input === null || input === undefined) return undefined;
  const out = String(input);
  return out.length > 0 ? out : undefined;
}

function normalizeMediaStatus(input?: string): TelemedicineSession["mediaStatus"] {
  const value = String(input ?? "").toUpperCase();
  if (value === "PROVISIONED" || value === "WAITING" || value === "IN_CALL" || value === "RECONNECTING" || value === "DEGRADED_AUDIO" || value === "ENDED") {
    return value as TelemedicineSession["mediaStatus"];
  }
  return undefined;
}
