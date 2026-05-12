/**
 * VITO Match Hooks — Health OS §1 (Identity Registry Match Management)
 *
 * Hooks for managing identity match candidates and resolutions via the Experience BFF.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type MatchDisposition = "PENDING" | "CONFIRMED" | "REJECTED";

export interface MatchResult {
  matchId: string;
  sourceHealthId: string;
  candidateHealthId: string;
  score: number;
  status: MatchDisposition;
  matchedAt: string;
}

/** Trigger identity match searching for a specific Health ID. */
export function useFindMatches() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (healthId: string) =>
      apiClient.post<ApiResponse<MatchResult[]>>(`/internal/v1/vito/match/${healthId}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "match"] });
    },
  });
}

/** List pending identity match candidates. */
export function usePendingMatches(page = 0, size = 20) {
  return useQuery({
    queryKey: ["vito", "match", "pending", page, size],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedItems<MatchResult>>>(
        `/internal/v1/vito/match/pending?page=${page}&size=${size}`
      ),
  });
}

/** Resolve a match candidate with a disposition (Confirm/Reject). */
export function useResolveMatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      matchId,
      disposition,
    }: {
      matchId: string;
      disposition: MatchDisposition;
    }) =>
      apiClient.post<ApiResponse<void>>(`/internal/v1/vito/match/${matchId}/resolve`, {
        disposition,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "match"] });
    },
  });
}
