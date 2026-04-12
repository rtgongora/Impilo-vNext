/**
 * Member-facing coverage self-service — TanStack Query hooks (Experience BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function unwrapArray(res: unknown): unknown[] {
  if (!res) return [];
  if (Array.isArray(res)) return res;
  const obj = res as { data?: unknown };
  if (Array.isArray(obj.data)) return obj.data;
  return [];
}

function withQuery(path: string, params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    search.set(k, String(v));
  }
  const qs = search.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

export function useMyCoveragePlans(cpid?: string | null) {
  return useQuery({
    queryKey: ["member-coverage-plans", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/plans", { member_cpid: cpid ?? undefined }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useMyEligibility(cpid?: string | null) {
  return useQuery({
    queryKey: ["member-coverage-eligibility", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/eligibility", { member_cpid: cpid ?? undefined }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useMyClaimHistory(cpid?: string | null, page = 0, size = 20) {
  return useQuery({
    queryKey: ["member-coverage-claims", cpid ?? null, page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/claims", {
          member_cpid: cpid ?? undefined,
          page,
          size,
        }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useMyContributions(cpid?: string | null) {
  return useQuery({
    queryKey: ["member-coverage-contributions", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/contributions", { member_cpid: cpid ?? undefined }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useMyPreauths(cpid?: string | null) {
  return useQuery({
    queryKey: ["member-coverage-preauths", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/preauths", { member_cpid: cpid ?? undefined }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useMyAppeals(cpid?: string | null) {
  return useQuery({
    queryKey: ["member-coverage-appeals", cpid ?? null],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/coverage/appeals", { appellant_id: cpid ?? undefined }),
      );
      return unwrapArray(res);
    },
    enabled: Boolean(cpid),
  });
}

export function useSubmitAppeal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/appeals", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["member-coverage-appeals"] });
    },
  });
}

export function useCheckEligibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/coverage/eligibility/check", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["member-coverage-eligibility"] });
    },
  });
}
