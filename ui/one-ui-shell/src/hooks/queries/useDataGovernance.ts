/**
 * Data & governance admin — rules (data-governance-service), governed access requests (DAGS),
 * datasets for lineage, and export evaluation (AI governance BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { AccessRequestResource } from "./useDataAccessGovernance";

function asArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (value && typeof value === "object") {
    const o = value as Record<string, unknown>;
    if (Array.isArray(o.items)) return o.items;
    if (Array.isArray(o.rules)) return o.rules;
    if (Array.isArray(o.datasets)) return o.datasets;
  }
  return [];
}

/** Active governance rules from data-governance-service (via AI governance BFF). */
export function useGovernanceRules() {
  return useQuery({
    queryKey: ["data-governance", "rules"],
    queryFn: async () => {
      const raw = await apiClient.get<ApiResponse<unknown> | { data: unknown }>("/internal/v1/ai-governance/rules");
      const data = "data" in raw ? raw.data : raw;
      return asArray(data);
    },
  });
}

/** Pending and recent access / export requests (DAGS access request workflow). */
export function useExportRequests() {
  return useQuery({
    queryKey: ["data-governance", "export-requests"],
    queryFn: async () => {
      const raw = await apiClient.get<ApiResponse<AccessRequestResource[]>>(
        "/internal/v1/governance/access/requests?page=0&size=50",
      );
      return raw.data ?? [];
    },
  });
}

/** Registered datasets — used as lineage / provenance anchors. */
export function useDataLineage(datasetId?: string) {
  return useQuery({
    queryKey: ["data-governance", "lineage", datasetId ?? "all"],
    queryFn: async () => {
      const raw = await apiClient.get<ApiResponse<unknown> | { data: unknown }>("/internal/v1/ai-governance/datasets");
      const data = "data" in raw ? raw.data : raw;
      let rows = asArray(data);
      if (datasetId) {
        rows = rows.filter((row) => {
          const r = row as Record<string, unknown>;
          const id = r.id ?? (r.attributes as Record<string, unknown> | undefined)?.id;
          return String(id ?? "") === datasetId;
        });
      }
      return rows;
    },
  });
}

export type CreateExportEvaluationBody = {
  dataset: string;
  purposeOfUse: string;
  format?: string;
  filters?: string;
};

export function useCreateExportRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateExportEvaluationBody) =>
      apiClient.post<unknown>("/internal/v1/ai-governance/exports/evaluate", {
        dataset: body.dataset,
        purposeOfUse: body.purposeOfUse,
        format: body.format ?? "JSON",
        filters: body.filters ?? "",
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["data-governance", "export-requests"] });
    },
  });
}

export function useApproveExport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; notes?: string }) =>
      apiClient.post<unknown>(`/internal/v1/governance/access/requests/${encodeURIComponent(args.id)}/approve`, {
        notes: args.notes,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["data-governance", "export-requests"] });
      void qc.invalidateQueries({ queryKey: ["governance", "access-requests"] });
    },
  });
}

export function useDenyExport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; notes?: string }) =>
      apiClient.post<unknown>(`/internal/v1/governance/access/requests/${encodeURIComponent(args.id)}/deny`, {
        notes: args.notes,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["data-governance", "export-requests"] });
      void qc.invalidateQueries({ queryKey: ["governance", "access-requests"] });
    },
  });
}
