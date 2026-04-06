/**
 * Wellness Service — Activity tracking, vitals, mood, and challenges.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { WellnessActivity, WellnessVital, MoodEntry, WellnessChallenge } from "../types";

const V1 = "/internal/v1/mobile/citizen/wellness";

export async function fetchActivities(patientId: string, days = 7): Promise<WellnessActivity[]> {
  const response = await apiClient.get<{ data: WellnessActivity[] }>(`${V1}/activities?patientId=${patientId}&days=${days}`);
  return response.data.data;
}

export async function logActivity(body: Partial<WellnessActivity> & { patientId: string }): Promise<void> {
  await apiClient.post(`${V1}/activities`, body);
}

export async function fetchVitals(patientId: string, type?: string): Promise<WellnessVital[]> {
  const params = type ? `&type=${type}` : "";
  const response = await apiClient.get<{ data: WellnessVital[] }>(`${V1}/vitals?patientId=${patientId}${params}`);
  return response.data.data;
}

export async function logVital(body: { patientId: string; vitalType: string; value: number; unit: string; source?: string }): Promise<void> {
  await apiClient.post(`${V1}/vitals`, body);
}

export async function fetchMoodLog(patientId: string): Promise<MoodEntry[]> {
  const response = await apiClient.get<{ data: MoodEntry[] }>(`${V1}/mood?patientId=${patientId}`);
  return response.data.data;
}

export async function logMood(body: { patientId: string; moodScore: number; energyLevel?: number; stressLevel?: number; notes?: string }): Promise<void> {
  await apiClient.post(`${V1}/mood`, body);
}

export async function fetchChallenges(): Promise<WellnessChallenge[]> {
  const response = await apiClient.get<{ data: WellnessChallenge[] }>(`${V1}/challenges`);
  return response.data.data;
}

export async function joinChallenge(challengeId: string, patientId: string): Promise<void> {
  await apiClient.post(`${V1}/challenges/${challengeId}/join`, { patientId });
}
