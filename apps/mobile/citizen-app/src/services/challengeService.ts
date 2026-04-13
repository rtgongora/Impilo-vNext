/**
 * Challenge & Goals Service — Wellness challenges and personal goals.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/wellness/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { WellnessChallenge, WellnessGoal } from "../types";

const V1 = "/internal/v1/mobile/citizen/wellness";

export async function fetchChallenges(): Promise<WellnessChallenge[]> {
  const response = await apiClient.get<{ data: WellnessChallenge[] }>(`${V1}/challenges`);
  return response.data.data ?? [];
}

export async function joinChallenge(id: string): Promise<void> {
  await apiClient.post(`${V1}/challenges/${encodeURIComponent(id)}/join`, {});
}

export async function fetchMyGoals(): Promise<WellnessGoal[]> {
  const response = await apiClient.get<{ data: WellnessGoal[] }>(`${V1}/goals`);
  return response.data.data ?? [];
}

export async function createGoal(payload: {
  title: string;
  description?: string;
  targetValue: number;
  targetDate: string;
}): Promise<WellnessGoal> {
  const response = await apiClient.post<{ data: WellnessGoal }>(`${V1}/goals`, payload);
  return response.data.data;
}

export async function updateGoalProgress(
  id: string,
  progress: number,
): Promise<WellnessGoal> {
  const response = await apiClient.patch<{ data: WellnessGoal }>(
    `${V1}/goals/${encodeURIComponent(id)}`,
    { currentValue: progress },
  );
  return response.data.data;
}
