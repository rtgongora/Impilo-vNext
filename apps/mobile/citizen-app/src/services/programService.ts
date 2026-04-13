/**
 * Program Service — Wellness programs and enrollments.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/wellness/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { WellnessProgram, Enrollment } from "../types";

const V1 = "/internal/v1/mobile/citizen/wellness";

export async function fetchPrograms(): Promise<WellnessProgram[]> {
  const response = await apiClient.get<{ data: WellnessProgram[] }>(`${V1}/programs`);
  return response.data.data ?? [];
}

export async function enrollInProgram(programId: string): Promise<Enrollment> {
  const response = await apiClient.post<{ data: Enrollment }>(`${V1}/enrollments`, { programId });
  return response.data.data;
}

export async function fetchMyEnrollments(): Promise<Enrollment[]> {
  const response = await apiClient.get<{ data: Enrollment[] }>(`${V1}/enrollments`);
  return response.data.data ?? [];
}
