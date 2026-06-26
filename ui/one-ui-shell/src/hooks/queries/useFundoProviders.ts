"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** C1 — learning-provider & academy registry (3 regulated kinds). */

export function useFundoProviders(kind?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (kind) qs.set("kind", kind);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "providers", kind ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/providers?${qs.toString()}`),
  });
}

export function useCreateFundoProvider() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/providers", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "providers"] }),
  });
}

export function useFundoProviderSpaces(providerId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "provider-spaces", providerId],
    enabled: Boolean(providerId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/providers/${encodeURIComponent(providerId!)}/spaces`),
  });
}

export function useCreateFundoSpace(providerId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/providers/${encodeURIComponent(providerId)}/spaces`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "provider-spaces", providerId] }),
  });
}
