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

export interface PublicHealthInvestigation {
  id: string;
  title: string;
  status: string;
  caseId: string;
  outbreakId: string;
  facilityId: string;
  assignedTo: string;
}

function normalizeInvestigation(resource: unknown): PublicHealthInvestigation {
  const r = asRecord(resource);
  return {
    id: String(r.id ?? ""),
    title: String(r.title ?? "Investigation"),
    status: String(r.status ?? "OPEN"),
    caseId: String(r.caseId ?? r.case_id ?? ""),
    outbreakId: String(r.outbreakId ?? r.outbreak_id ?? ""),
    facilityId: String(r.facilityId ?? r.facility_id ?? ""),
    assignedTo: String(r.assignedTo ?? r.assigned_to ?? ""),
  };
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

export function useInvestigations() {
  return useQuery({
    queryKey: ["surveillance", "investigations"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown }>("/internal/v1/public-health/investigations");
      const rows = extractPublicHealthList(response.data, ["items"]);
      return rows.map(normalizeInvestigation);
    },
  });
}

export function useOpenInvestigation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/public-health/investigations", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["surveillance", "investigations"] });
      void qc.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export function useUpdateInvestigationStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { investigationId: string; status: string; findings?: string }) =>
      apiClient.post<unknown>(
        `/internal/v1/public-health/investigations/${encodeURIComponent(args.investigationId)}/status`,
        { status: args.status, findings: args.findings },
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["surveillance", "investigations"] });
      void qc.invalidateQueries({ queryKey: ["public-health-operations-home"] });
    },
  });
}

export type { PublicHealthAlert, PublicHealthCase, PublicHealthCounter, PublicHealthSignal };
