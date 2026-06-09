/**
 * Public health field operations — mobile BFF (`ProviderPublicHealthController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/mobile/provider/public-health";

export interface FieldTaskRow {
  id: string;
  title: string;
  status: string;
  task_type: string;
  assigned_to?: string | null;
}

export interface OperationsHomeKpis {
  active_signals?: number;
  open_cases?: number;
  open_alerts?: number;
  active_outbreaks?: number;
  field_tasks_open?: number;
}

export interface SiteRegistryRow {
  siteId: string;
  name: string;
  province: string;
  district: string;
  regulatoryStatus: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface MapMarkerRow {
  id: string;
  label: string;
  markerType: string;
  status: string;
  latitude: number;
  longitude: number;
}

export interface InvestigationRow {
  id: string;
  title: string;
  status: string;
}

function extractItems(raw: unknown): unknown[] {
  if (!raw || typeof raw !== "object") return [];
  const o = raw as Record<string, unknown>;
  const data = o.data && typeof o.data === "object" ? (o.data as Record<string, unknown>) : o;
  if (Array.isArray(data.items)) return data.items;
  if (Array.isArray(data)) return data;
  return [];
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function normalizeTask(row: unknown): FieldTaskRow {
  const r = row as Record<string, unknown>;
  return {
    id: String(r.id ?? r.task_id ?? ""),
    title: String(r.title ?? "Field task"),
    status: String(r.status ?? "PLANNED"),
    task_type: String(r.task_type ?? r.taskType ?? "FIELD_OPERATION"),
    assigned_to: r.assigned_to != null ? String(r.assigned_to) : r.assignedTo != null ? String(r.assignedTo) : null,
  };
}

function readNumber(row: Record<string, unknown>, ...keys: string[]): number | null {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() !== "") {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return null;
}

function normalizeSite(row: unknown): SiteRegistryRow {
  const r = asRecord(row);
  return {
    siteId: String(r.siteId ?? r.site_id ?? ""),
    name: String(r.name ?? "Site"),
    province: String(r.province ?? ""),
    district: String(r.district ?? ""),
    regulatoryStatus: String(r.regulatoryStatus ?? r.regulatory_status ?? ""),
    latitude: readNumber(r, "latitude", "lat"),
    longitude: readNumber(r, "longitude", "lng", "lon"),
  };
}

function normalizeMarker(row: unknown): MapMarkerRow | null {
  const r = asRecord(row);
  const lat = readNumber(r, "latitude", "lat");
  const lng = readNumber(r, "longitude", "lng", "lon");
  if (lat == null || lng == null) return null;
  return {
    id: String(r.id ?? ""),
    label: String(r.label ?? "Marker"),
    markerType: String(r.markerType ?? r.marker_type ?? "unknown"),
    status: String(r.status ?? ""),
    latitude: lat,
    longitude: lng,
  };
}

function normalizeInvestigation(row: unknown): InvestigationRow {
  const r = asRecord(row);
  return {
    id: String(r.id ?? ""),
    title: String(r.title ?? "Investigation"),
    status: String(r.status ?? "OPEN"),
  };
}

export async function fetchFieldTasks(): Promise<FieldTaskRow[]> {
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/field-tasks`);
  return extractItems(r.data).map(normalizeTask);
}

export async function fetchOperationsHomeKpis(): Promise<OperationsHomeKpis> {
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/operations-home`);
  const data = r.data?.data as Record<string, unknown> | undefined;
  const kpis = (data?.kpis ?? {}) as OperationsHomeKpis;
  return kpis;
}

const TASK_NEXT: Record<string, string> = {
  PLANNED: "ASSIGNED",
  ASSIGNED: "IN_PROGRESS",
  IN_PROGRESS: "COMPLETED",
};

export function nextFieldTaskStatus(current: string): string | null {
  return TASK_NEXT[current] ?? null;
}

export async function transitionFieldTask(taskId: string, status: string): Promise<void> {
  await apiClient.post(`${BASE}/field-tasks/${encodeURIComponent(taskId)}/transition`, { status });
}

export interface CreateFieldTaskInput {
  activityTitle: string;
  activityType?: string;
  status?: string;
  province?: string;
  district?: string;
}

export async function createFieldTask(input: CreateFieldTaskInput): Promise<FieldTaskRow> {
  const r = await apiClient.post<{ data: unknown }>(`${BASE}/field-tasks`, {
    activity_title: input.activityTitle,
    activity_type: input.activityType ?? "Community screening",
    status: input.status ?? "PLANNED",
    province: input.province ?? "",
    district: input.district ?? "",
  });
  return normalizeTask(asRecord(r.data).data ?? r.data);
}

export async function submitFieldOperation(body: Record<string, unknown>): Promise<void> {
  await apiClient.post(`${BASE}/field-operations`, body);
}

export async function fetchMapMarkers(): Promise<MapMarkerRow[]> {
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/map-markers`);
  return extractItems(r.data)
    .map(normalizeMarker)
    .filter((row): row is MapMarkerRow => row != null);
}

export async function fetchSiteRegistrySites(page = 0, size = 20): Promise<SiteRegistryRow[]> {
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/site-registry/sites?page=${page}&size=${size}`);
  return extractItems(r.data).map(normalizeSite);
}

export async function fetchInvestigations(): Promise<InvestigationRow[]> {
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/investigations`);
  return extractItems(r.data).map(normalizeInvestigation);
}
