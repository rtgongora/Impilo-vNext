"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** A4 — academic programs + terms (pre-service foundation). */

export function useFundoPrograms(limit = 100) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "academic", "programs", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/academic/programs?limit=${limit}`),
  });
}

export function useCreateFundoProgram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/academic/programs", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic", "programs"] }),
  });
}

export function useFundoTerms(programId?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (programId) qs.set("programId", programId);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "academic", "terms", programId ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/academic/terms?${qs.toString()}`),
  });
}

export function useCreateFundoTerm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/academic/terms", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic", "terms"] }),
  });
}
