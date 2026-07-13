/**
 * Experience UI — Telemedicine Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface TelemedicineSession {
  id: string;
  type: "TelemedicineSession";
  attributes: {
    encounter_id: string | null;
    patient_id: string | null;
    provider_id: string | null;
    facility_id: string | null;
    session_type: string;
    status: string;
    room_url: string | null;
    scheduled_at: string | null;
    started_at: string | null;
    ended_at: string | null;
    duration_seconds: number | null;
    notes: string | null;
    referral_id: string | null;
    created_at: string;
    updated_at: string;
    token?: string;
    channel?: string;
  };
}

interface CreateTelemedicineSessionPayload {
  encounter_id?: string;
  patient_id: string;
  provider_id?: string;
  facility_id?: string;
  referral_id?: string;
  session_type?: string;
  scheduled_at?: string;
  notes?: string;
  session_provider?: string;
  purpose_of_use?: string;
  consent_reference?: string;
  /** Teleconsult referral composer fields (BFF create accepts them alongside scheduling fields). */
  urgency?: string;
  specialty?: string;
}

type SessionsResponse = ApiResponse<TelemedicineSession[]>;
type SessionResponse = ApiResponse<TelemedicineSession>;
type TelemedicineOpsResponse = ApiResponse<{
  facilityId: string;
  submittedReferralBacklog: number;
  inProgressSessions: number;
  scheduledSessions: number;
  overdueScheduledSessions: number;
}>;

export function useTelemedicineSessions(params?: {
  providerId?: string;
  patientId?: string;
  facilityId?: string;
  referralId?: string;
  status?: string;
}) {
  const queryParams = new URLSearchParams();
  if (params?.providerId) queryParams.set("referrerId", params.providerId);
  if (params?.patientId) queryParams.set("patientId", params.patientId);
  if (params?.facilityId) queryParams.set("facility_id", params.facilityId);
  if (params?.referralId) queryParams.set("referral_id", params.referralId);
  if (params?.status) queryParams.set("status", params.status);
  const qs = queryParams.toString();

  return useQuery<SessionsResponse>({
    queryKey: ["telemedicine-sessions", params],
    queryFn: () =>
      apiClient.get<SessionsResponse>(
        `/internal/v1/teleconsult/sessions${qs ? `?${qs}` : ""}`
      ),
  });
}

export function useJoinTelemedicineSession() {
  const queryClient = useQueryClient();

  return useMutation<SessionResponse, unknown, { id: string }>({
    mutationFn: ({ id }) =>
      apiClient.post<SessionResponse>(
        `/internal/v1/teleconsult/sessions/${id}/join`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
    },
  });
}

export function useEndTelemedicineSession() {
  const queryClient = useQueryClient();

  return useMutation<
    SessionResponse,
    unknown,
    { id: string; notes?: string }
  >({
    mutationFn: ({ id, notes }) =>
      apiClient.post<SessionResponse>(
        `/internal/v1/teleconsult/sessions/${id}/end`,
        notes ? { notes } : undefined
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
    },
  });
}

/** Governed Stage-4 accept — carries acceptance context and emits the telemedicine audit + notification. */
export function useAcceptTeleconsultSession() {
  const queryClient = useQueryClient();
  return useMutation<
    SessionResponse,
    unknown,
    { id: string; receivingFacilityId?: string; receivingFacilityName?: string; scheduledAt?: string; notes?: string }
  >({
    mutationFn: ({ id, receivingFacilityId, receivingFacilityName, scheduledAt, notes }) =>
      apiClient.post<SessionResponse>(`/internal/v1/teleconsult/sessions/${id}/accept`, {
        receiving_facility_id: receivingFacilityId,
        receiving_facility_name: receivingFacilityName,
        scheduled_at: scheduledAt,
        notes,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      queryClient.invalidateQueries({ queryKey: ["telemedicine-specialty-workbench"] });
      queryClient.invalidateQueries({ queryKey: ["incoming-referrals"] });
    },
  });
}

/** Governed Stage-4 decline — a reason is mandatory (the BFF rejects a blank one). */
export function useDeclineTeleconsultSession() {
  const queryClient = useQueryClient();
  return useMutation<SessionResponse, unknown, { id: string; reason: string }>({
    mutationFn: ({ id, reason }) =>
      apiClient.post<SessionResponse>(`/internal/v1/teleconsult/sessions/${id}/decline`, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      queryClient.invalidateQueries({ queryKey: ["telemedicine-specialty-workbench"] });
      queryClient.invalidateQueries({ queryKey: ["incoming-referrals"] });
    },
  });
}

export function useCreateTelemedicineSession() {
  const queryClient = useQueryClient();

  return useMutation<SessionResponse, unknown, CreateTelemedicineSessionPayload>(
    {
      mutationFn: (payload) =>
        apiClient.post<SessionResponse>(
          `/internal/v1/teleconsult/sessions`,
          payload
        ),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      },
    }
  );
}

/** Participant roles the RTC media-token endpoint accepts. */
export type TelemedicineMediaRole = "PROVIDER" | "PATIENT" | "CAREGIVER" | "INTERPRETER";
/** Media profile requested for the token (AUDIO_ONLY = audio-first / low bandwidth). */
export type TelemedicineMediaProfile = "FULL" | "AUDIO_ONLY";

/**
 * Media-token response payload. Two shapes share this type:
 *   granted  → { room_url, token, mediaProfile? }
 *   gated    → { status: "WAITING" | "DENIED", sessionId, identity }
 * Patients poll while WAITING (the provider admits from the waiting room).
 */
export interface TelemedicineMediaTokenPayload {
  room_url?: string;
  roomUrl?: string;
  token?: string;
  accessToken?: string;
  channel?: string;
  mediaProfile?: TelemedicineMediaProfile | string;
  status?: "WAITING" | "DENIED" | string;
  sessionId?: string;
  identity?: string;
}

export interface TelemedicineMediaTokenVariables {
  sessionId: string;
  displayName?: string;
  role?: TelemedicineMediaRole;
  mediaProfile?: TelemedicineMediaProfile;
}

export function useTelemedicineMediaToken() {
  return useMutation<
    ApiResponse<TelemedicineMediaTokenPayload>,
    unknown,
    TelemedicineMediaTokenVariables
  >({
    mutationFn: ({ sessionId, role, displayName, mediaProfile }) =>
      apiClient.post(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/media/token`,
        { role, displayName, mediaProfile },
      ),
  });
}

/** Refresh an expiring media token; returns the same grant/gated shapes as issue. */
export function useTelemedicineMediaTokenRefresh() {
  return useMutation<
    ApiResponse<TelemedicineMediaTokenPayload>,
    unknown,
    { sessionId: string; displayName?: string; role?: TelemedicineMediaRole }
  >({
    mutationFn: ({ sessionId, role, displayName }) =>
      apiClient.post(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/media/token/refresh`,
        { role, displayName },
      ),
  });
}

export interface TelemedicineWaitingRoomEntry {
  identity: string;
  displayName: string;
  role: string;
  state: string;
  requestedAt: string;
}

type WaitingRoomResponse = ApiResponse<{ waiting: TelemedicineWaitingRoomEntry[] }>;

/** Provider-only waiting room list; polls while the session page is open. */
export function useTelemedicineWaitingRoom(
  sessionId: string | null | undefined,
  options?: { enabled?: boolean; refetchIntervalMs?: number },
) {
  return useQuery<WaitingRoomResponse>({
    queryKey: ["telemedicine-waiting-room", sessionId ?? null],
    enabled: Boolean(sessionId) && (options?.enabled ?? true),
    refetchInterval: options?.refetchIntervalMs ?? 5_000,
    queryFn: () =>
      apiClient.get<WaitingRoomResponse>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(String(sessionId))}/waiting-room`,
      ),
  });
}

export function useAdmitTelemedicineParticipant(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { identity: string }>({
    mutationFn: ({ identity }) =>
      apiClient.post(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/admit`,
        { identity },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-waiting-room", sessionId] });
    },
  });
}

export function useDenyTelemedicineParticipant(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { identity: string; reason?: string }>({
    mutationFn: ({ identity, reason }) =>
      apiClient.post(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/deny`,
        { identity, reason },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["telemedicine-waiting-room", sessionId] });
    },
  });
}

export type TelemedicineRtcHealth = {
  provider: string;
  devModeEnabled: boolean;
  livekitEnabled: boolean;
  livekitConfigured: boolean;
  productionReady: boolean;
  serverUrl: string;
  activeSessions?: number;
};

type TelemedicineRtcHealthResponse = ApiResponse<TelemedicineRtcHealth>;

export function useTelemedicineRtcHealth() {
  return useQuery<TelemedicineRtcHealthResponse>({
    queryKey: ["telemedicine-rtc-health"],
    queryFn: () =>
      apiClient.get<TelemedicineRtcHealthResponse>("/internal/v1/teleconsult/ops/rtc-health"),
    staleTime: 30_000,
  });
}

export function useTelemedicineOpsSla(facilityId?: string | null) {
  const path = facilityId
    ? `/internal/v1/teleconsult/ops/sla?facility_id=${encodeURIComponent(String(facilityId))}`
    : "/internal/v1/teleconsult/ops/sla";
  return useQuery<TelemedicineOpsResponse>({
    queryKey: ["telemedicine-ops-sla", facilityId ?? null],
    queryFn: () => apiClient.get<TelemedicineOpsResponse>(path),
    staleTime: 15_000,
  });
}

export function useTelemedicineSpecialtyWorkbench(params: {
  facilityId?: string | null;
  specialty?: string | null;
  page?: number;
  size?: number;
}) {
  const qp = new URLSearchParams();
  if (params.facilityId) qp.set("facility_id", String(params.facilityId));
  if (params.specialty) qp.set("specialty", params.specialty);
  qp.set("page", String(params.page ?? 0));
  qp.set("size", String(params.size ?? 50));
  const qs = qp.toString();
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["telemedicine-specialty-workbench", params.facilityId ?? null, params.specialty ?? null, params.page ?? 0, params.size ?? 50],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(`/internal/v1/teleconsult/ops/specialty-workbench${qs ? `?${qs}` : ""}`),
    staleTime: 15_000,
  });
}
