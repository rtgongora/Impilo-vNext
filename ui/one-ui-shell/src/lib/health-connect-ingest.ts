/**
 * Impilo Health Connect parity client (Experience BFF).
 * @see https://developer.android.com/health-and-fitness/health-connect
 */

import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/wellness/connect/v1";
const PERSONAL_DATA_BASE = "/internal/v1/wellness/personal-data";

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

export type WellnessConnectedSource = {
  id: string;
  patient_id: string;
  source_type: string;
  source_name: string;
  source_device_id?: string | null;
  source_app_id?: string | null;
  status: string;
  provider_access_allowed: boolean;
  clinical_writeback_allowed: boolean;
  consent_status: string;
  sharing_scope: string;
  category_permissions?: Record<string, boolean>;
  updated_at?: string;
};

export type WellnessSourceCreateRequest = {
  person_cpid: string;
  source_type: string;
  source_name: string;
  source_device_id?: string;
  source_app_id?: string;
  source_priority?: number;
  provider_access_allowed?: boolean;
  clinical_writeback_allowed?: boolean;
  consent_status?: string;
  sharing_scope?: string;
  category_permissions?: Record<string, boolean>;
};

export async function listPersonalHealthSources(personCpid: string): Promise<WellnessConnectedSource[]> {
  const res = await apiClient.get<{ data: WellnessConnectedSource[] }>(
    `${PERSONAL_DATA_BASE}/sources?person_cpid=${encodeURIComponent(personCpid)}`,
  );
  return res.data ?? [];
}

export async function connectPersonalHealthSource(body: WellnessSourceCreateRequest): Promise<{ id: string }> {
  const res = await apiClient.post<{ data: { id: string } }>(`${PERSONAL_DATA_BASE}/sources`, body);
  return res.data;
}

export async function updatePersonalHealthSourcePermissions(
  sourceId: string,
  body: Partial<{
    provider_access_allowed: boolean;
    clinical_writeback_allowed: boolean;
    consent_status: string;
    sharing_scope: string;
    status: string;
    category_permissions: Record<string, boolean>;
  }>,
): Promise<void> {
  await apiClient.patch(`${PERSONAL_DATA_BASE}/sources/${encodeURIComponent(sourceId)}/permissions`, body);
}

export async function getPersonalWellnessSummary(patientId: string): Promise<Record<string, unknown>> {
  const res = await apiClient.get<{ data: Record<string, unknown> }>(
    `${PERSONAL_DATA_BASE}/summary?person_cpid=${encodeURIComponent(patientId)}`,
  );
  return res.data ?? {};
}
