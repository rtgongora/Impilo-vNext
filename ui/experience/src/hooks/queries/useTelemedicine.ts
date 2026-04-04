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
  session_type: string;
  scheduled_at?: string;
  notes?: string;
}

type SessionsResponse = ApiResponse<TelemedicineSession[]>;
type SessionResponse = ApiResponse<TelemedicineSession>;

export function useTelemedicineSessions(params?: {
  providerId?: string;
  status?: string;
}) {
  const queryParams = new URLSearchParams();
  if (params?.providerId) queryParams.set("provider_id", params.providerId);
  if (params?.status) queryParams.set("status", params.status);
  const qs = queryParams.toString();

  return useQuery<SessionsResponse>({
    queryKey: ["telemedicine-sessions", params],
    queryFn: () =>
      apiClient.get<SessionsResponse>(
        `/internal/v1/mobile/provider/telemedicine/sessions${qs ? `?${qs}` : ""}`
      ),
  });
}

export function useJoinTelemedicineSession() {
  const queryClient = useQueryClient();

  return useMutation<SessionResponse, unknown, { id: string }>({
    mutationFn: ({ id }) =>
      apiClient.post<SessionResponse>(
        `/internal/v1/mobile/provider/telemedicine/sessions/${id}/join`
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
        `/internal/v1/mobile/provider/telemedicine/sessions/${id}/end`,
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
          `/internal/v1/mobile/provider/telemedicine/sessions`,
          payload
        ),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ["telemedicine-sessions"] });
      },
    }
  );
}
