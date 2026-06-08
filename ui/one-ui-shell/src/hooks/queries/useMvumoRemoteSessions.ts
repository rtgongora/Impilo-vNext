import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useMvumoRemoteSession(sessionId: string | undefined) {
  return useQuery({
    queryKey: ["mvumo-remote-session", sessionId],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/mvumo/remote-sessions/${encodeURIComponent(String(sessionId))}`,
      ),
    enabled: !!sessionId,
  });
}

export function useCreateMvumoRemoteSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/mvumo/remote-sessions", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["mvumo-remote-session"] }),
  });
}

export function useVerifyMvumoRemoteSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, token }: { sessionId: string; token?: string }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/mvumo/remote-sessions/${encodeURIComponent(sessionId)}/verify`,
        token ? { token } : {},
      ),
    onSuccess: (_d, vars) => void qc.invalidateQueries({ queryKey: ["mvumo-remote-session", vars.sessionId] }),
  });
}

export function useGrantMvumoRemoteSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, body }: { sessionId: string; body?: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/mvumo/remote-sessions/${encodeURIComponent(sessionId)}/grant`,
        body ?? {},
      ),
    onSuccess: (_d, vars) => void qc.invalidateQueries({ queryKey: ["mvumo-remote-session", vars.sessionId] }),
  });
}

export function useRefuseMvumoRemoteSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, reason }: { sessionId: string; reason?: string }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/mvumo/remote-sessions/${encodeURIComponent(sessionId)}/refuse`,
        reason ? { reason } : {},
      ),
    onSuccess: (_d, vars) => void qc.invalidateQueries({ queryKey: ["mvumo-remote-session", vars.sessionId] }),
  });
}
