/**
 * Surveillance plane — TanStack Query hooks for signals, cases, counters, alerts, and ingest.
 * BFF: `/internal/v1/public-health/*` → surveillance-service.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import {
  extractPublicHealthList,
  normalizeAlert,
  normalizeCase,
  normalizeSignal,
  parseCountersPayload,
  type PublicHealthAlert,
  type PublicHealthCase,
  type PublicHealthCounter,
  type PublicHealthSignal,
} from "./usePublicHealth";

function q(params: Record<string, string | undefined>): string {
  const u = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== "") u.set(k, v);
  }
  const s = u.toString();
  return s ? `?${s}` : "";
}

export type SurveillanceSignalFilters = { status?: string; syndrome?: string };
export type SurveillanceCaseFilters = { status?: string };

export function useSignals(filters?: SurveillanceSignalFilters) {
  return useQuery({
    queryKey: ["surveillance", "signals", filters ?? {}],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/signals");
      let rows: PublicHealthSignal[] = extractPublicHealthList(response.data, ["items"]).map(normalizeSignal);
      if (filters?.status) {
        const st = filters.status.toUpperCase();
        rows = rows.filter((r) => r.status.toUpperCase() === st);
      }
      if (filters?.syndrome) {
        const s = filters.syndrome.toLowerCase();
        rows = rows.filter((r) => r.disease.toLowerCase().includes(s));
      }
      return rows;
    },
  });
}

export function useCases(filters?: SurveillanceCaseFilters) {
  return useQuery({
    queryKey: ["surveillance", "cases", filters?.status ?? ""],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>(
        `/internal/v1/public-health/cases${q({ status: filters?.status })}`,
      );
      return extractPublicHealthList(response.data, ["items"]).map(normalizeCase);
    },
  });
}

export function useCounters(facilityId?: string, period?: { from?: string; to?: string }) {
  return useQuery({
    queryKey: ["surveillance", "counters", facilityId ?? "", period?.from ?? "", period?.to ?? ""],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>(
        `/internal/v1/public-health/counters${q({ from: period?.from, to: period?.to })}`,
      );
      let rows = parseCountersPayload(response.data);
      if (facilityId) {
        rows = rows.filter((c) => c.detail.includes(facilityId) || c.id.includes(facilityId));
      }
      return rows;
    },
  });
}

export function useAlerts() {
  return useQuery({
    queryKey: ["surveillance", "alerts"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/alerts");
      return extractPublicHealthList(response.data, ["alerts"]).map(normalizeAlert);
    },
  });
}

function invalidateSurveillanceQueries(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["surveillance"] });
  void qc.invalidateQueries({ queryKey: ["public-health-signals"] });
  void qc.invalidateQueries({ queryKey: ["public-health-cases"] });
  void qc.invalidateQueries({ queryKey: ["public-health-counters"] });
  void qc.invalidateQueries({ queryKey: ["public-health-alerts"] });
}

export function useCreateSignal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/public-health/signals", body),
    onSuccess: () => invalidateSurveillanceQueries(qc),
  });
}

export type IngestEventPayload = {
  eventType: string;
  payload?: string;
  facilityId?: string | null;
};

function uuidOrUndefined(v: string | null | undefined): string | undefined {
  if (!v || !/^[0-9a-fA-F-]{36}$/.test(v.trim())) return undefined;
  return v.trim();
}

export function useIngestEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: IngestEventPayload) =>
      apiClient.post<unknown>("/internal/v1/public-health/ingest", {
        eventType: body.eventType,
        payload: body.payload ?? "{}",
        facilityId: uuidOrUndefined(body.facilityId ?? undefined),
      }),
    onSuccess: () => invalidateSurveillanceQueries(qc),
  });
}

/** Opens a surveillance case via ingest (cases are opened from signal hits / ingest in surveillance-service). */
export function useCreateCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { eventType: string; title?: string; facilityId?: string | null; syndrome?: string }) =>
      apiClient.post<unknown>("/internal/v1/public-health/ingest", {
        eventType: body.eventType,
        payload: JSON.stringify({
          title: body.title,
          syndrome: body.syndrome,
          source: "MANUAL_CASE_INTAKE",
        }),
        facilityId: uuidOrUndefined(body.facilityId ?? undefined),
      }),
    onSuccess: () => invalidateSurveillanceQueries(qc),
  });
}

export type { PublicHealthAlert, PublicHealthCase, PublicHealthCounter, PublicHealthSignal };
