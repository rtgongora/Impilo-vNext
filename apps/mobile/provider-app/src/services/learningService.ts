import { apiClient } from "@impilo/mobile-api-client";
import type { ProviderLearningSummary } from "../types";

interface LearningSubject {
  subjectType: string;
  subjectId: string;
}

function getObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function summarizeProviderLearning(payload: unknown): ProviderLearningSummary {
  const root = getObject(payload);

  const inProgress = asArray(root.inProgress).length || asNumber(root.inProgressCount) || 0;
  const overdue = asArray(root.overdue).length || asNumber(root.overdueCount) || 0;
  const required =
    asArray(root.required).length || asNumber(root.requiredCount) || overdue;
  const cpdEligible =
    asArray(root.cpdEligibleCompletions).length || asNumber(root.cpdEligibleCount) || 0;

  return { inProgress, required, overdue, cpdEligible };
}

export async function getProviderLearningSummary(
  subject: LearningSubject,
): Promise<ProviderLearningSummary> {
  const response = await apiClient.get<{ data?: unknown }>(
    `/internal/v1/learning/v11/my-learning?subjectType=${encodeURIComponent(subject.subjectType)}&subjectId=${encodeURIComponent(subject.subjectId)}`,
  );
  return summarizeProviderLearning(response.data?.data);
}
