import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function extractList(payload: unknown): unknown[] {
  const root = asRecord(payload);
  const data = asRecord(root.data ?? root);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data.items)) return data.items as unknown[];
  if (Array.isArray(data.datasets)) return data.datasets as unknown[];
  if (Array.isArray(data.rows)) return data.rows as unknown[];
  return [];
}

export interface WarehouseGoldDataset {
  datasetKey: string;
  rowCount: number | null;
  lastMaterializedAt: string;
}

function normalizeDataset(row: unknown): WarehouseGoldDataset {
  const r = asRecord(row);
  const count = r.rowCount ?? r.row_count;
  return {
    datasetKey: String(r.datasetKey ?? r.dataset_key ?? r.dataset ?? ""),
    rowCount: typeof count === "number" ? count : count != null ? Number(count) : null,
    lastMaterializedAt: String(r.lastMaterializedAt ?? r.last_materialized_at ?? ""),
  };
}

export function useWarehouseGoldStats() {
  return useQuery({
    queryKey: ["warehouse", "gold-stats"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/warehouse/gold/stats");
      return asRecord(response.data);
    },
    staleTime: 30_000,
  });
}

export function useWarehouseGoldDatasets() {
  return useQuery({
    queryKey: ["warehouse", "gold-datasets"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/warehouse/gold/datasets");
      return extractList(response.data).map(normalizeDataset);
    },
    staleTime: 30_000,
  });
}

export function useWarehouseGoldQuery(dataset: string, page = 0, limit = 25) {
  return useQuery({
    queryKey: ["warehouse", "gold-query", dataset, page, limit],
    enabled: dataset.length > 0,
    queryFn: async () => {
      const sp = new URLSearchParams({ dataset, page: String(page), limit: String(limit) });
      const response = await apiClient.get<{ data: unknown }>(`/internal/v1/warehouse/gold/query?${sp}`);
      return asRecord(response.data);
    },
  });
}

export function useMaterializeWarehouseGold() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/warehouse/gold/materialize", body),
  });
}

export function useNdrCatalogDatasets() {
  return useQuery({
    queryKey: ["ndr-catalog", "datasets"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/ndr-catalog/datasets");
      return extractList(response.data);
    },
    staleTime: 30_000,
  });
}
