/**
 * Wellness Service — Activity tracking, vitals, mood, and challenges.
 * Maps JDBC/snake_case rows from Experience BFF into app TypeScript models.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { WellnessActivity, WellnessVital, MoodEntry, WellnessChallenge } from "../types";

const V1 = "/internal/v1/mobile/citizen/wellness";

type Row = Record<string, unknown>;

function num(v: unknown, fallback = 0): number {
  if (typeof v === "number" && !Number.isNaN(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isNaN(n) ? fallback : n;
  }
  return fallback;
}

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}

/** Exported for unit tests */
export function mapWellnessActivityRow(row: Row): WellnessActivity {
  return {
    id: str(row.id, "activity"),
    activityDate: str(row.activity_date ?? row.activityDate, ""),
    steps: num(row.steps),
    caloriesBurned: num(row.calories_burned ?? row.caloriesBurned),
    activeMinutes: num(row.active_minutes ?? row.activeMinutes),
    distanceKm: num(row.distance_km ?? row.distanceKm),
    sleepHours: row.sleep_hours != null || row.sleepHours != null ? num(row.sleep_hours ?? row.sleepHours) : undefined,
    sleepQuality: row.sleep_quality != null ? str(row.sleep_quality) : row.sleepQuality != null ? str(row.sleepQuality) : undefined,
    waterMl: num(row.water_ml ?? row.waterMl),
  };
}

export function mapWellnessVitalRow(row: Row): WellnessVital {
  return {
    id: str(row.id, "vital"),
    vitalType: str(row.vital_type ?? row.vitalType, "UNKNOWN"),
    value: num(row.value),
    unit: str(row.unit, ""),
    measuredAt: str(row.measured_at ?? row.measuredAt, ""),
    source: str(row.source, "MANUAL"),
    notes: row.notes != null ? str(row.notes) : undefined,
  };
}

export function mapMoodEntryRow(row: Row): MoodEntry {
  return {
    id: str(row.id, "mood"),
    moodScore: num(row.mood_score ?? row.moodScore, 3),
    energyLevel: row.energy_level != null || row.energyLevel != null ? num(row.energy_level ?? row.energyLevel) : undefined,
    stressLevel: row.stress_level != null || row.stressLevel != null ? num(row.stress_level ?? row.stressLevel) : undefined,
    notes: row.notes != null ? str(row.notes) : undefined,
    loggedAt: str(row.logged_at ?? row.loggedAt, ""),
  };
}

export function mapWellnessChallengeRow(row: Row): WellnessChallenge {
  return {
    id: str(row.id, ""),
    title: str(row.title, "Challenge"),
    description: row.description != null ? str(row.description) : undefined,
    challengeType: str(row.challenge_type ?? row.challengeType, "STEPS"),
    targetValue: num(row.target_value ?? row.targetValue),
    targetUnit: str(row.target_unit ?? row.targetUnit, ""),
    startDate: str(row.start_date ?? row.startDate, ""),
    endDate: str(row.end_date ?? row.endDate, ""),
    participantCount: num(row.participant_count ?? row.participantCount),
    status: str(row.status, "ACTIVE"),
  };
}

export async function fetchActivities(patientId: string, days = 7): Promise<WellnessActivity[]> {
  const response = await apiClient.get<{ data: Row[] }>(`${V1}/activities?patientId=${encodeURIComponent(patientId)}&days=${days}`);
  const rows = response.data.data ?? [];
  return rows.map(mapWellnessActivityRow);
}

export async function logActivity(body: Partial<WellnessActivity> & { patientId: string }): Promise<void> {
  await apiClient.post(`${V1}/activities`, body);
}

export async function fetchVitals(patientId: string, type?: string): Promise<WellnessVital[]> {
  const params = type ? `&type=${encodeURIComponent(type)}` : "";
  const response = await apiClient.get<{ data: Row[] }>(
    `${V1}/vitals?patientId=${encodeURIComponent(patientId)}${params}`,
  );
  const rows = response.data.data ?? [];
  return rows.map(mapWellnessVitalRow);
}

export async function logVital(body: { patientId: string; vitalType: string; value: number; unit: string; source?: string }): Promise<void> {
  await apiClient.post(`${V1}/vitals`, body);
}

export async function fetchMoodLog(patientId: string): Promise<MoodEntry[]> {
  const response = await apiClient.get<{ data: Row[] }>(`${V1}/mood?patientId=${encodeURIComponent(patientId)}`);
  const rows = response.data.data ?? [];
  return rows.map(mapMoodEntryRow);
}

export async function logMood(body: { patientId: string; moodScore: number; energyLevel?: number; stressLevel?: number; notes?: string }): Promise<void> {
  await apiClient.post(`${V1}/mood`, body);
}

export async function fetchChallenges(): Promise<WellnessChallenge[]> {
  const response = await apiClient.get<{ data: Row[] }>(`${V1}/challenges`);
  const rows = response.data.data ?? [];
  return rows.map(mapWellnessChallengeRow);
}

export async function joinChallenge(challengeId: string, patientId: string): Promise<void> {
  await apiClient.post(`${V1}/challenges/${challengeId}/join`, { patientId });
}
