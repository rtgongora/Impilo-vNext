import { getVashandi, postVashandi } from "./client";
import type { VashandiActionResponse } from "../types";

export interface TrainingRequirement {
  id?: string;
  roleTemplateId?: string;
  courseCode?: string;
  enforcementLevel?: "ADVISORY" | "SOFT" | "HARD" | string;
  active?: boolean;
  note?: string | null;
  createdBy?: string | null;
  createdAt?: string;
  [key: string]: unknown;
}

export async function listTrainingRequirements(params?: Record<string, string | undefined>) {
  return getVashandi<TrainingRequirement[]>("/training-requirements", params);
}

export async function createTrainingRequirement(body: {
  roleTemplateId: string;
  courseCode: string;
  enforcementLevel?: string;
  note?: string;
}) {
  return postVashandi<TrainingRequirement>("/training-requirements", body);
}

export async function deactivateTrainingRequirement(id: string) {
  return postVashandi<TrainingRequirement>(
    `/training-requirements/${encodeURIComponent(id)}/deactivate`,
    {},
  );
}

export function trainingRequirementsFromResponse(
  response: VashandiActionResponse,
): TrainingRequirement[] {
  const data = response.data;
  if (Array.isArray(data)) return data as TrainingRequirement[];
  if (data && typeof data === "object") {
    const record = data as Record<string, unknown>;
    if (Array.isArray(record.items)) return record.items as TrainingRequirement[];
  }
  return [];
}
