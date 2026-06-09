import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function extractList(payload: unknown): unknown[] {
  const root = asRecord(payload);
  const data = asRecord(root.data ?? root);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data.items)) return data.items as unknown[];
  if (Array.isArray(data.watermarks)) return data.watermarks as unknown[];
  if (Array.isArray(data.sources)) return data.sources as unknown[];
  return [];
}

export interface PipelineWatermarkRow {
  sourceId: string;
  lastEventId: string;
  lastProcessedAt: string;
  lagSeconds: number | null;
}

export interface PipelineSourceRow {
  sourceId: string;
  sourceType: string;
  status: string;
}

function normalizeWatermark(row: unknown): PipelineWatermarkRow {
  const r = asRecord(row);
  const lag = r.lagSeconds ?? r.lag_seconds;
  return {
    sourceId: String(r.sourceId ?? r.source_id ?? ""),
    lastEventId: String(r.lastEventId ?? r.last_event_id ?? ""),
    lastProcessedAt: String(r.lastProcessedAt ?? r.last_processed_at ?? ""),
    lagSeconds: typeof lag === "number" ? lag : lag != null ? Number(lag) : null,
  };
}

function normalizeSource(row: unknown): PipelineSourceRow {
  const r = asRecord(row);
  return {
    sourceId: String(r.sourceId ?? r.source_id ?? ""),
    sourceType: String(r.sourceType ?? r.source_type ?? ""),
    status: String(r.status ?? "ACTIVE"),
  };
}

export function useDataPipelineWatermarks() {
  return useQuery({
    queryKey: ["data-pipeline", "watermarks"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/pipeline/watermarks");
      return extractList(response.data).map(normalizeWatermark);
    },
    staleTime: 30_000,
  });
}

export function useDataPipelineSources() {
  return useQuery({
    queryKey: ["data-pipeline", "sources"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/pipeline/sources");
      return extractList(response.data).map(normalizeSource);
    },
    staleTime: 30_000,
  });
}
