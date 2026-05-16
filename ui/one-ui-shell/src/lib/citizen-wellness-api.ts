/**
 * Citizen wellness API — same BFF contract as mobile (`/internal/v1/mobile/citizen/wellness/*`).
 */

import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/mobile/citizen/wellness";

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

export interface WellnessActivity {
  id: string;
  activityDate: string;
  steps: number;
  caloriesBurned: number;
  activeMinutes: number;
  distanceKm: number;
  sleepHours?: number;
  sleepQuality?: string;
  waterMl: number;
}

export interface WellnessChallenge {
  id: string;
  title: string;
  description?: string;
  challengeType: string;
  targetValue: number;
  targetUnit: string;
  startDate: string;
  endDate: string;
  participantCount: number;
  status: string;
}

export interface WellnessDiscoverService {
  id: string;
  name: string;
  description?: string;
  category?: string;
  facilityName?: string;
  price?: number;
  currency?: string;
  rating?: number;
}

function mapActivityRow(row: Row): WellnessActivity {
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

function mapChallengeRow(row: Row): WellnessChallenge {
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

export async function fetchWellnessActivities(patientId: string, days = 14): Promise<WellnessActivity[]> {
  const res = await apiClient.get<{ data: Row[] }>(
    `${BASE}/activities?patientId=${encodeURIComponent(patientId)}&days=${days}`,
  );
  const rows = res.data ?? [];
  return rows.map(mapActivityRow);
}

export async function logWellnessActivity(body: {
  patientId: string;
  date?: string;
  steps?: number;
  caloriesBurned?: number;
  activeMinutes?: number;
  distanceKm?: number;
  sleepHours?: number;
  sleepQuality?: string | number;
  waterMl?: number;
}): Promise<void> {
  const payload: Record<string, unknown> = {
    patientId: body.patientId,
    steps: body.steps ?? 0,
    caloriesBurned: body.caloriesBurned ?? 0,
    activeMinutes: body.activeMinutes ?? 0,
    distanceKm: body.distanceKm ?? 0,
    waterMl: body.waterMl ?? 0,
  };
  if (body.date) payload.date = body.date;
  if (body.sleepHours != null) payload.sleepHours = body.sleepHours;
  if (body.sleepQuality != null) payload.sleepQuality = String(body.sleepQuality);
  await apiClient.post(`${BASE}/activities`, payload);
}

export async function fetchWellnessChallenges(): Promise<WellnessChallenge[]> {
  const res = await apiClient.get<{ data: Row[] }>(`${BASE}/challenges`);
  const rows = res.data ?? [];
  return rows.map(mapChallengeRow);
}

export async function joinWellnessChallenge(challengeId: string, patientId: string): Promise<void> {
  await apiClient.post(`${BASE}/challenges/${challengeId}/join`, { patientId });
}

export async function fetchWellnessDiscoverServices(category?: string): Promise<WellnessDiscoverService[]> {
  const query = category ? `?category=${encodeURIComponent(category)}` : "";
  const res = await apiClient.get<{ data: Row[] }>(`/internal/v1/mobile/citizen/services/discover${query}`);
  const rows = res.data ?? [];
  return rows.map((row) => ({
    id: str(row.id, ""),
    name: str(row.name, "Service"),
    description: row.description != null ? str(row.description) : undefined,
    category: row.category != null ? str(row.category) : undefined,
    facilityName: row.facility_name != null ? str(row.facility_name) : row.facilityName != null ? str(row.facilityName) : undefined,
    price: row.price != null ? num(row.price) : undefined,
    currency: row.currency != null ? str(row.currency) : undefined,
    rating: row.rating != null ? num(row.rating) : undefined,
  }));
}
