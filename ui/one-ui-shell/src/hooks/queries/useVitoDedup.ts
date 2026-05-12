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

export interface DedupScore {
  score: number;
  matchDetails?: Record<string, unknown>;
  conflictingFields?: string[];
}

export interface DedupCase {
  id: string;
  status: "PENDING" | "MERGED" | "DISMISSED" | "REJECTED";
  score: number;
  sourceHealthId: string;
  targetHealthId: string;
  sourceClient?: Record<string, unknown>;
  targetClient?: Record<string, unknown>;
  createdAt: string;
  updatedAt?: string;
}

export interface MergeRequest {
  sourceHealthId: string;
  targetHealthId: string;
  notes?: string;
  mergeReason?: string;
}

export function useDedupCases(status?: string, page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: ["vito", "dedup", "cases", { status, page, size }],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedItems<DedupCase>>>(
        `/internal/v1/vito/dedup/cases?status=${status || ""}&page=${page}&size=${size}`
      ),
  });
}

export function useScoreDedup() {
  return useMutation({
    mutationFn: (body: { sourceHealthId: string; targetHealthId: string }) =>
      apiClient.post<ApiResponse<DedupScore>>("/internal/v1/vito/dedup/score", body),
  });
}

export function useMergeDedup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ caseId, body }: { caseId: string; body: MergeRequest }) =>
      apiClient.post<ApiResponse<{ mergeId: string }>>(
        `/internal/v1/vito/dedup/cases/${caseId}/merge`,
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "dedup"] });
    },
  });
}

export function useUnmergeDedup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (mergeId: string) =>
      apiClient.post<ApiResponse<{ status: string }>>(
        `/internal/v1/vito/dedup/merge/${mergeId}/unmerge`,
        {}
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "dedup"] });
    },
  });
}
