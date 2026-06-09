import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { ProcedureEpisode } from "./useProcedureEpisode";

export type ProcedureConsentLive = {
  mvumo?: Record<string, unknown>;
  episode?: Record<string, unknown>;
};

export type ConsentLifecycleResult = {
  mvumo?: Record<string, unknown>;
  episode?: ProcedureEpisode;
  session?: Record<string, unknown>;
};

export function useProcedureConsentLive(episodeId: string, mvumoRequestId?: string) {
  return useQuery({
    queryKey: ["procedure-consent-live", episodeId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: ProcedureConsentLive }>(
        `/internal/v1/procedures/episodes/${episodeId}/consent/live`
      );
      return res.data;
    },
    enabled: !!episodeId && !!mvumoRequestId,
    refetchInterval: (query) => {
      const state = String(query.state.data?.mvumo?.state ?? query.state.data?.episode?.consent_status ?? "");
      if (state === "GRANTED" || state === "REFUSED") return false;
      return 5000;
    },
  });
}

export function useProcedureConsentCapture(episodeId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["procedure-episode", episodeId] });
    void qc.invalidateQueries({ queryKey: ["procedure-consent-live", episodeId] });
  };

  return {
    requestConsent: useMutation({
      mutationFn: () =>
        apiClient.post<{ data: ProcedureEpisode }>(`/internal/v1/procedures/episodes/${episodeId}/consent`, {}),
      onSuccess: invalidate,
    }),
    syncConsent: useMutation({
      mutationFn: () =>
        apiClient.post<{ data: ProcedureEpisode }>(`/internal/v1/procedures/episodes/${episodeId}/consent/sync`, {}),
      onSuccess: invalidate,
    }),
    provideExplanation: useMutation({
      mutationFn: (body?: Record<string, unknown>) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/explanation`,
          body ?? {}
        ),
      onSuccess: invalidate,
    }),
    verifyIdentity: useMutation({
      mutationFn: (body?: Record<string, unknown>) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/verify-identity`,
          body ?? {}
        ),
      onSuccess: invalidate,
    }),
    grantConsent: useMutation({
      mutationFn: (body?: Record<string, unknown>) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/grant`,
          body ?? {}
        ),
      onSuccess: invalidate,
    }),
    refuseConsent: useMutation({
      mutationFn: (reason?: string) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/refuse`,
          reason ? { reason } : {}
        ),
      onSuccess: invalidate,
    }),
    createRemoteSession: useMutation({
      mutationFn: (body?: Record<string, unknown>) =>
        apiClient.post<{ data: Record<string, unknown> }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/remote-session`,
          body ?? { channel: "TOKEN_LINK", ttlMinutes: 30 }
        ),
    }),
    verifyRemoteSession: useMutation({
      mutationFn: ({ sessionId, token }: { sessionId: string; token: string }) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/remote-session/${sessionId}/verify`,
          { token }
        ),
      onSuccess: invalidate,
    }),
    grantRemoteSession: useMutation({
      mutationFn: ({ sessionId, body }: { sessionId: string; body?: Record<string, unknown> }) =>
        apiClient.post<{ data: ConsentLifecycleResult }>(
          `/internal/v1/procedures/episodes/${episodeId}/consent/remote-session/${sessionId}/grant`,
          body ?? {}
        ),
      onSuccess: invalidate,
    }),
  };
}
