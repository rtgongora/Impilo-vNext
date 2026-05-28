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
