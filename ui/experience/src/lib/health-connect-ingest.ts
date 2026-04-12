/**
 * Impilo Health Connect parity client (Experience BFF).
 * @see https://developer.android.com/health-and-fitness/health-connect
 */

import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/wellness/connect/v1";

export type HealthConnectDataOrigin = {
  platform: string;
  appPackage?: string;
  appVersion?: string;
};

/** Superset of fields aligned with Android Health Connect flattened JSON. */
export type HealthConnectRecord = {
  id: string;
  type: string;
  metadata?: { recording_method?: string; device?: string; clientRecordId?: string; dataOriginPackage?: string };
  startTime: string;
  endTime?: string;
  startZoneOffset?: string;
  endZoneOffset?: string;
  count?: number;
  /** Liters — alias `volume` accepted by BFF */
  volume?: number;
  volumeLiters?: number;
  hoursSlept?: number;
  sleepQuality?: number;
  sleepStages?: Array<{ stage: string; startTime: string; endTime: string }>;
  beatsPerMinute?: number;
  samples?: Array<{ time: string; beatsPerMinute: number }>;
  distance?: number;
  distanceMeters?: number;
  floors?: number;
  floorsClimbed?: number;
  activeEnergyKcal?: number;
  totalEnergyKcal?: number;
  weightKg?: number;
  heightCm?: number;
  bodyFatPercent?: number;
  bloodPressureSystolic?: number;
  bloodPressureDiastolic?: number;
  bloodGlucose?: number;
  oxygenSaturation?: number;
  restingHeartRate?: number;
  hrvRmssdMs?: number;
  nutritionEnergyKcal?: number;
  energy?: number;
  exerciseType?: string;
  exerciseTitle?: string;
  exerciseEnergyKcal?: number;
  exerciseDistanceMeters?: number;
  wheelchairPushes?: number;
  elevationGainedMeters?: number;
  activeMinutes?: number;
  stepsCadenceRpm?: number;
  cyclingPedalingCadenceRpm?: number;
  powerWatts?: number;
  speedMetersPerSecond?: number;
  respiratoryRateBpm?: number;
  bodyTemperatureCelsius?: number;
  basalMetabolicRateKcal?: number;
  leanBodyMassKg?: number;
  boneMassKg?: number;
  vo2MaxMlKgMin?: number;
  bodyWaterMassKg?: number;
  mindfulnessSessionMinutes?: number;
  nutritionProteinG?: number;
  nutritionCarbsG?: number;
  nutritionFatG?: number;
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

export async function getHealthConnectSleepSegments(patientId: string, limit = 100): Promise<unknown[]> {
  const res = await apiClient.get<{ data: unknown[] }>(
    `${BASE}/sleep-segments?patientId=${encodeURIComponent(patientId)}&limit=${limit}`,
  );
  return res.data ?? [];
}

export async function getHealthConnectExerciseSessions(patientId: string, days = 30): Promise<unknown[]> {
  const res = await apiClient.get<{ data: unknown[] }>(
    `${BASE}/exercise-sessions?patientId=${encodeURIComponent(patientId)}&days=${days}`,
  );
  return res.data ?? [];
}

export async function getHealthConnectIngestLog(
  patientId: string,
  limit = 100,
  includePayload = false,
): Promise<unknown[]> {
  const q = new URLSearchParams({ patientId, limit: String(limit), includePayload: String(includePayload) });
  const res = await apiClient.get<{ data: unknown[] }>(`${BASE}/ingest-log?${q.toString()}`);
  return res.data ?? [];
}

export async function getHealthConnectExtensionRecords(patientId: string, limit = 100): Promise<unknown[]> {
  const res = await apiClient.get<{ data: unknown[] }>(
    `${BASE}/extension-records?patientId=${encodeURIComponent(patientId)}&limit=${limit}`,
  );
  return res.data ?? [];
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
