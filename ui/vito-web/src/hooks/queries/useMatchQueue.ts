import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { MatchResult, MatchDisposition, PagedResponse } from "@/types/vito";

export interface ListMatchParams {
  page?: number;
  size?: number;
}

export const matchKeys = {
  all: ["match"] as const,
  pending: (params: ListMatchParams) => [...matchKeys.all, "pending", params] as const,
  detail: (matchId: string) => [...matchKeys.all, "detail", matchId] as const,
};

export function usePendingMatches(params: ListMatchParams = {}) {
  return useQuery({
    queryKey: matchKeys.pending(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<MatchResult>>(
        `/v1/match/pending${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
  });
}

export function useTriggerMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (healthId: string) =>
      vitoApiClient.post<MatchResult>(`/v1/match/trigger`, { healthId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: matchKeys.all });
    },
  });
}

export function useResolveMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      matchId,
      disposition,
    }: {
      matchId: string;
      disposition: MatchDisposition;
    }) =>
      vitoApiClient.post<MatchResult>(`/v1/match/${matchId}/resolve`, {
        disposition,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: matchKeys.all });
    },
  });
}
