import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type { DedupCase, DedupStatus, PagedResponse } from "@/types/vito";

export interface ListDedupParams {
  page?: number;
  size?: number;
  disposition?: DedupStatus;
}

export const dedupKeys = {
  all: ["dedup"] as const,
  list: (params: ListDedupParams) => [...dedupKeys.all, "list", params] as const,
  detail: (caseId: string) => [...dedupKeys.all, "detail", caseId] as const,
};

export function useDedupCases(params: ListDedupParams = {}) {
  return useQuery({
    queryKey: dedupKeys.list(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      if (params.disposition) searchParams.set("disposition", params.disposition);
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<DedupCase>>(
        `/v1/internal/dedup/cases${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
  });
}

export function useMergeCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      caseId,
      survivorCpid,
    }: {
      caseId: string;
      survivorCpid: string;
    }) =>
      vitoApiClient.post<DedupCase>(`/v1/internal/dedup/${caseId}/merge`, { survivorCpid }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dedupKeys.all });
    },
  });
}

export function useUnmergeCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (caseId: string) =>
      vitoApiClient.post<DedupCase>(`/v1/internal/dedup/${caseId}/unmerge`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dedupKeys.all });
    },
  });
}

export function useResolveDedupCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      caseId,
      action,
      survivorCpid,
      notes,
    }: {
      caseId: string;
      action: "MERGE" | "NOT_DUPLICATE" | "DEFERRED";
      survivorCpid?: string;
      notes?: string;
    }) =>
      vitoApiClient.post<DedupCase>(`/v1/registry/dedup/${caseId}/resolve`, {
        action,
        survivorCpid,
        notes,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dedupKeys.all });
    },
  });
}

export function useRecalculateScore() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (caseId: string) =>
      vitoApiClient.post<DedupCase>(`/v1/internal/dedup/score`, { caseId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: dedupKeys.all });
    },
  });
}
