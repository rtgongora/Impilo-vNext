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

export function useTelemedicineSessions(
  params?: {
    providerId?: string;
    patientId?: string;
    facilityId?: string;
    referralId?: string;
    status?: string;
  },
  options?: { enabled?: boolean },
) {
  const queryParams = new URLSearchParams();
  if (params?.providerId) queryParams.set("referrerId", params.providerId);
  if (params?.patientId) queryParams.set("patientId", params.patientId);
  if (params?.facilityId) queryParams.set("facility_id", params.facilityId);
  if (params?.referralId) queryParams.set("referral_id", params.referralId);
  if (params?.status) queryParams.set("status", params.status);
  const qs = queryParams.toString();

  return useQuery<SessionsResponse>({
    queryKey: ["telemedicine-sessions", params],
    // The BFF list requires a patient/referrer/facility filter (else 400). Callers guard
    // with enabled until a filter is available rather than firing a doomed request.
    enabled: options?.enabled ?? true,
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

/** TM-B1 reason-bound lifecycle transitions the guard exposes on a referral. */
export type ReferralLifecycleActionName = "cancel" | "reopen" | "escalate" | "transfer";

/** Guard-permitted next actions for a referral's current state (drives the action menu). */
export interface ReferralAllowedActions {
  referralId: string;
  status: string;
  allowedTargets: string[];
}

/**
 * TM-B1 — the guard-permitted next states for a referral. Enabled only when a session id is
 * present. The `allowedTargets` list is the source of truth for which lifecycle buttons render;
 * the UI never guesses transitions locally.
 */
export function useReferralAllowedActions(sessionId: string | null | undefined) {
  return useQuery<ApiResponse<ReferralAllowedActions>>({
    queryKey: ["teleconsult-allowed-actions", sessionId ?? null],
    enabled: Boolean(sessionId),
    queryFn: () =>
      apiClient.get<ApiResponse<ReferralAllowedActions>>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(String(sessionId))}/allowed-actions`,
      ),
  });
}

/**
 * TM-B1 — post a reason-bound lifecycle transition (cancel/reopen/escalate/transfer). A reason is
 * mandatory (the BFF rejects a blank one with REASON_REQUIRED). Invalidates the referral's
 * allowed-actions plus the session/worklist queries so contextual UI re-derives after the move.
 */
export function useReferralLifecycleAction(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<Record<string, unknown>>,
    unknown,
    { action: ReferralLifecycleActionName; reason: string }
  >({
    mutationFn: ({ action, reason }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/${action}`,
        { reason },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teleconsult-allowed-actions", sessionId] });
      queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      queryClient.invalidateQueries({ queryKey: ["telemedicine-specialty-workbench"] });
      queryClient.invalidateQueries({ queryKey: ["incoming-referrals"] });
    },
  });
}

// ── TM-B7: in-session orders + tasks (execution loop) ──────────────────────

/** A task linked to a teleconsult referral (follow-up, result review, patient action). */
export interface ReferralTask {
  taskId: string;
  referralId: string | null;
  sourceRef: string | null;
  taskType: string;
  assigneeId: string | null;
  assigneeRole: string | null;
  status: string;
  blocksClosure: boolean;
  dueAt: string | null;
  notes: string | null;
  createdBy: string | null;
  createdAt: string | null;
  completedBy: string | null;
  completedAt: string | null;
}

/** TM-B7 — tasks scoped to a teleconsult session (referral). */
export function useReferralTasks(sessionId: string | null | undefined) {
  return useQuery<ApiResponse<ReferralTask[]>>({
    queryKey: ["teleconsult-tasks", sessionId ?? null],
    enabled: Boolean(sessionId),
    queryFn: () =>
      apiClient.get<ApiResponse<ReferralTask[]>>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(String(sessionId))}/tasks`,
      ),
  });
}

/** TM-B7 — create a follow-up / execution task on the current teleconsult session. */
export function useCreateReferralTask(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<ReferralTask>,
    unknown,
    { taskType: string; notes?: string; assigneeRole?: string; dueAt?: string; blocksClosure?: boolean }
  >({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<ReferralTask>>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/tasks`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teleconsult-tasks", sessionId] });
      queryClient.invalidateQueries({ queryKey: ["teleconsult-allowed-actions", sessionId] });
    },
  });
}

/** TM-B7 — place a diagnostic/clinical order from within the teleconsult session. */
export function usePlaceReferralOrder(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<Record<string, unknown>>,
    unknown,
    { orderType: string; patientCpid: string; ziboOrderCode?: string; priority?: string; clinicalNotes?: string }
  >({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/teleconsult/sessions/${encodeURIComponent(sessionId)}/orders`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teleconsult-tasks", sessionId] });
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

/**
 * Pool-queue context for POOL-routed teleconsults. Present only when the case is served
 * from a shared pool; `null`/absent for direct (non-pool) sessions. Read-only signal the
 * provider waiting room surfaces so the consulting clinician sees queue pressure.
 */
export interface TelemedicineWaitingRoomPoolContext {
  poolId: string;
  /** People currently waiting in the pool. */
  depth: number;
  /** Longest current wait in minutes, or null when unknown. */
  oldestWaitingMinutes: number | null;
  /** Estimated wait for the next admit, in minutes. */
  estimatedWaitMinutes: number;
}

type WaitingRoomResponse = ApiResponse<{
  waiting: TelemedicineWaitingRoomEntry[];
  /** Pool-queue context (POOL routing only); null/absent for direct sessions. */
  poolContext?: TelemedicineWaitingRoomPoolContext | null;
}>;

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
