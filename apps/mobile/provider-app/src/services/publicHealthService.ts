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

function extractItems(raw: unknown): unknown[] {
  if (!raw || typeof raw !== "object") return [];
  const o = raw as Record<string, unknown>;
  const data = o.data && typeof o.data === "object" ? (o.data as Record<string, unknown>) : o;
  if (Array.isArray(data.items)) return data.items;
  if (Array.isArray(data)) return data;
  return [];
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
