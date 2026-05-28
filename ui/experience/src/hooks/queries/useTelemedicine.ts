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
  facility_id: string;
  referral_id?: string;
  session_type: string;
  scheduled_at?: string;
  notes?: string;
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
