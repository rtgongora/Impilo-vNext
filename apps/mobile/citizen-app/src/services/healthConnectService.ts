/**
 * Health Connect parity — batch ingest + read-back (Experience BFF).
 * Map @react-native-health-connect / HealthKit records into this payload; keep stable `records[].id`.
 */
import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/wellness/connect/v1";

export type HealthConnectChangeSet = {
  patientId: string;
  dataOrigin: { platform: string; appPackage?: string; appVersion?: string };
  grantedScopes?: string[];
  records: Array<Record<string, unknown>>;
};

export async function fetchHealthConnectManifest(): Promise<Record<string, unknown>> {
  const response = await apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/manifest`);
  return response.data.data ?? {};
}

export async function fetchHealthConnectSleepSegments(patientId: string, limit = 100): Promise<unknown[]> {
  const response = await apiClient.get<{ data: unknown[] }>(
    `${BASE}/sleep-segments?patientId=${encodeURIComponent(patientId)}&limit=${limit}`,
  );
  return response.data.data ?? [];
}

export async function fetchHealthConnectExerciseSessions(patientId: string, days = 30): Promise<unknown[]> {
  const response = await apiClient.get<{ data: unknown[] }>(
    `${BASE}/exercise-sessions?patientId=${encodeURIComponent(patientId)}&days=${days}`,
  );
  return response.data.data ?? [];
}

export async function ingestHealthConnectChangeSet(body: HealthConnectChangeSet): Promise<{
  applied: number;
  skipped: number;
  errors: string[];
}> {
  const response = await apiClient.post<{ data: { applied: number; skipped: number; errors: string[] } }>(
    `${BASE}/changesets`,
    body,
  );
  const d = response.data.data ?? { applied: 0, skipped: 0, errors: [] };
  return { applied: d.applied, skipped: d.skipped, errors: d.errors ?? [] };
}
