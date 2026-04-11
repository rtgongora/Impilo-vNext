/**
 * Health Connect–equivalent batch ingest (Experience BFF).
 * Wire this from a future @react-native-health-connect (or HealthKit) bridge:
 * map native records → this payload; stable `records[].id` enables idempotent sync.
 */
import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/wellness/connect/v1";

export type HealthConnectChangeSet = {
  patientId: string;
  dataOrigin: { platform: string; appPackage?: string; appVersion?: string };
  grantedScopes?: string[];
  records: Array<{
    id: string;
    type: string;
    startTime: string;
    endTime?: string;
    count?: number;
    volumeLiters?: number;
    hoursSlept?: number;
    sleepQuality?: number;
    beatsPerMinute?: number;
    samples?: Array<{ time: string; beatsPerMinute: number }>;
  }>;
};

export async function fetchHealthConnectManifest(): Promise<Record<string, unknown>> {
  const response = await apiClient.get<{ data: Record<string, unknown> }>(`${BASE}/manifest`);
  return response.data.data ?? {};
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
