/**
 * Health Intelligence — AI model registry, inference audit, drift monitoring (Experience BFF).
 * Endpoints: /internal/v1/ai/*
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type UnknownRecord = Record<string, unknown>;

function unwrapData<T = unknown>(res: unknown): T | undefined {
  if (res == null) return undefined;
  if (typeof res !== "object") return res as T;
  const o = res as { data?: T };
  if ("data" in o) return o.data;
  return res as T;
}

function unwrapRows(res: unknown): unknown[] {
  const d = unwrapData<unknown>(res);
  if (Array.isArray(d)) return d;
  if (d && typeof d === "object") {
    const o = d as Record<string, unknown>;
    if (Array.isArray(o.items)) return o.items;
    if (Array.isArray(o.content)) return o.content;
    if (Array.isArray(o.records)) return o.records;
  }
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

export function useAiModels(page = 0, size = 50) {
  return useQuery({
    queryKey: ["ai-models", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery("/internal/v1/ai/models", { page, size }),
      );
      return unwrapRows(res);
    },
  });
}

export function useAiModel(modelId: string | undefined) {
  return useQuery({
    queryKey: ["ai-model", modelId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/ai/models/${encodeURIComponent(String(modelId))}`,
      );
      return unwrapData(res);
    },
    enabled: Boolean(modelId),
  });
}

export function useAiInferenceRecords(page = 0, size = 50) {
  return useQuery({
    queryKey: ["ai-inference-records", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery("/internal/v1/ai/inference-records", { page, size }),
      );
      return unwrapRows(res);
    },
  });
}

export function useAiDriftEvents(page = 0, size = 50) {
  return useQuery({
    queryKey: ["ai-drift-events", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        withQuery("/internal/v1/ai/drift-events", { page, size }),
      );
      return unwrapRows(res);
    },
  });
}

export function useRegisterAiModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/ai/models", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ai-models"] });
    },
  });
}

export function useApproveAiModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (modelId: string) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/ai/models/${encodeURIComponent(modelId)}/approve`,
        {},
      ),
    onSuccess: (_, modelId) => {
      void qc.invalidateQueries({ queryKey: ["ai-models"] });
      void qc.invalidateQueries({ queryKey: ["ai-model", modelId] });
    },
  });
}

export function useWithdrawAiModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (modelId: string) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/ai/models/${encodeURIComponent(modelId)}/withdraw`,
        {},
      ),
    onSuccess: (_, modelId) => {
      void qc.invalidateQueries({ queryKey: ["ai-models"] });
      void qc.invalidateQueries({ queryKey: ["ai-model", modelId] });
    },
  });
}

export function useAddAiModelVersion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, body }: { modelId: string; body: UnknownRecord }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/ai/models/${encodeURIComponent(modelId)}/versions`,
        body,
      ),
    onSuccess: (_, { modelId }) => {
      void qc.invalidateQueries({ queryKey: ["ai-model", modelId] });
      void qc.invalidateQueries({ queryKey: ["ai-models"] });
    },
  });
}
