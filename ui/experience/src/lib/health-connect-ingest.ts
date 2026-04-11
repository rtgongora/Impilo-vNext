/**
 * Impilo Health Connect–equivalent ingest (BFF).
 * @see Android Health Connect https://developer.android.com/health-and-fitness/guides/health-connect
 */

import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/wellness/connect/v1";

export type HealthConnectDataOrigin = {
  platform: string;
  appPackage?: string;
  appVersion?: string;
};

export type HealthConnectRecord = {
  id: string;
  type: string;
  metadata?: { recording_method?: string; device?: string };
  startTime: string;
  endTime?: string;
  count?: number;
  volumeLiters?: number;
  hoursSlept?: number;
  sleepQuality?: number;
  beatsPerMinute?: number;
  samples?: Array<{ time: string; beatsPerMinute: number }>;
};

export type HealthConnectChangeSet = {
  patientId: string;
  dataOrigin: HealthConnectDataOrigin;
  grantedScopes?: string[];
  records: HealthConnectRecord[];
};

export async function getHealthConnectManifest(): Promise<Record<string, unknown>> {
  const res = await apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/manifest`);
  return res.data ?? {};
}

export async function postHealthConnectChangeSet(body: HealthConnectChangeSet): Promise<{
  applied: number;
  skipped: number;
  errors: string[];
}> {
  const res = await apiClient.post<{ data: { applied: number; skipped: number; errors: string[] } }>(
    `${BASE}/changesets`,
    body,
  );
  const d = res.data;
  return { applied: d.applied ?? 0, skipped: d.skipped ?? 0, errors: d.errors ?? [] };
}
