/**
 * Extension points — governed rules and form schemas (BFF /internal/v1/extensions/*).
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

function normalizeList(raw: unknown): unknown[] {
  const d = unwrapData(raw);
  if (Array.isArray(d)) return d;
  if (d && typeof d === "object") {
    const o = d as Record<string, unknown>;
    if (Array.isArray(o.content)) return o.content;
    if (Array.isArray(o.items)) return o.items;
    if (Array.isArray(o.forms)) return o.forms;
    if (Array.isArray(o.rules)) return o.rules;
  }
  return [];
}

export function useExtensionRules(page = 0, size = 50) {
  return useQuery({
    queryKey: ["extensions-rules", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/extensions/rules?page=${page}&size=${size}`,
      );
      return normalizeList(res);
    },
  });
}

export function useExtensionForms(page = 0, size = 50) {
  return useQuery({
    queryKey: ["extensions-forms", page, size],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown>(
        `/internal/v1/extensions/forms?page=${page}&size=${size}`,
      );
      return normalizeList(res);
    },
  });
}

export function useEvaluateExtensionRule() {
  return useMutation({
    mutationFn: ({ ruleId, facts }: { ruleId: string; facts: UnknownRecord }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/extensions/rules/${encodeURIComponent(ruleId)}/evaluate`,
        facts,
      ),
  });
}

/** Optional: when BFF adds POST /internal/v1/extensions/rules */
export function useCreateExtensionRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/extensions/rules", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["extensions-rules"] });
    },
  });
}

/** Optional: when BFF adds POST /internal/v1/extensions/forms */
export function useCreateExtensionForm() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UnknownRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/extensions/forms", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["extensions-forms"] });
    },
  });
}
