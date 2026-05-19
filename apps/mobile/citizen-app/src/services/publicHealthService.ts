import { apiClient } from "@impilo/mobile-api-client";

export interface CitizenPublicHealthSummary {
  kpis: Record<string, number>;
  priorityWorklist: Array<{ label: string; priority: string }>;
}

export interface CitizenPublicHealthAlert {
  id: string;
  syndromeCode: string;
  severity: string;
  acknowledged: boolean;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function listFrom(payload: unknown, key: string): unknown[] {
  const root = asRecord(payload);
  const data = asRecord(root.data);
  const candidate = data[key] ?? root[key];
  return Array.isArray(candidate) ? candidate : [];
}

export async function fetchCitizenPublicHealthSummary(): Promise<CitizenPublicHealthSummary> {
  const response = await apiClient.get<{ data: unknown }>("/internal/v1/mobile/citizen/public-health/summary");
  const root = asRecord(response.data);
  const payload = asRecord(root.data ?? root);
  const kpis = asRecord(payload.kpis);
  const normalizedKpis: Record<string, number> = {};
  for (const [key, val] of Object.entries(kpis)) {
    const parsed = Number(val);
    normalizedKpis[key] = Number.isFinite(parsed) ? parsed : 0;
  }
  const worklist = listFrom(payload, "priority_worklist").map((item) => {
    const row = asRecord(item);
    return {
      label: String(row.label ?? "Priority item"),
      priority: String(row.priority ?? "MEDIUM"),
    };
  });
  return { kpis: normalizedKpis, priorityWorklist: worklist };
}

export async function fetchCitizenPublicHealthAlerts(): Promise<CitizenPublicHealthAlert[]> {
  const response = await apiClient.get<{ data: unknown }>("/internal/v1/mobile/citizen/public-health/alerts");
  const root = asRecord(response.data);
  const items = listFrom(root, "alerts");
  return items.map((item) => {
    const row = asRecord(item);
    return {
      id: String(row.id ?? ""),
      syndromeCode: String(row.syndrome_code ?? row.syndromeCode ?? "UNKNOWN"),
      severity: String(row.severity ?? "MEDIUM"),
      acknowledged: Boolean(row.acknowledged),
    };
  });
}
