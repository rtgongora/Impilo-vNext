"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface BootstrapSnapshot {
  generatedAt: string;
  vito: Record<string, unknown>;
  varapi: Record<string, unknown>;
  tuso: Record<string, unknown>;
  indawo?: Record<string, unknown>;
}

export function useBootstrapSnapshot() {
  return useQuery({
    queryKey: ["registry-intake", "bootstrap"],
    queryFn: () => apiClient.get<ApiResponse<BootstrapSnapshot>>("/internal/v1/registry-intake/bootstrap/snapshot"),
  });
}

export function useCreateIntakeSession() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/registry-intake/sessions", body),
  });
}

export function useCreateRecoveryRequest() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/registry-intake/recovery-requests", body),
  });
}

export function useCreateImportJob() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/registry-intake/import-jobs", body),
  });
}

export function useExecuteImportJob() {
  return useMutation({
    mutationFn: ({ jobId, dryRun }: { jobId: string; dryRun: boolean }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/registry-intake/import-jobs/${jobId}/execute`,
        { dryRun },
      ),
  });
}

export type IndawoSiteSearchParams = {
  q?: string;
  cursor?: number;
  limit?: number;
};

export function useSearchIndawoSites() {
  return useMutation({
    mutationFn: (params: IndawoSiteSearchParams) => {
      const sp = new URLSearchParams();
      if (params.q != null && params.q !== "") sp.set("q", params.q);
      if (params.cursor != null) sp.set("cursor", String(params.cursor));
      if (params.limit != null) sp.set("limit", String(params.limit));
      const qs = sp.toString();
      const path =
        qs.length > 0
          ? `/internal/v1/registry-intake/indawo/sites?${qs}`
          : "/internal/v1/registry-intake/indawo/sites";
      return apiClient.get<ApiResponse<Record<string, unknown>>>(path);
    },
  });
}
