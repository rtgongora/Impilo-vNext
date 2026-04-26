"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export function useLearningSubjectCompletions(
  subjectType: string | undefined,
  subjectId: string | undefined,
  limit = 25,
) {
  return useQuery({
    queryKey: ["learning", "subject-completions", subjectType, subjectId, limit],
    enabled: Boolean(subjectType && subjectId),
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/learning/subject-completions?subjectType=${encodeURIComponent(subjectType!)}&subjectId=${encodeURIComponent(subjectId!)}&limit=${limit}`,
      ),
  });
}

export function useLearningResourceDetail(resourceId: string | undefined) {
  return useQuery({
    queryKey: ["learning", "resource", resourceId],
    enabled: Boolean(resourceId),
    queryFn: () => apiClient.get<unknown>(`/internal/v1/learning/resources/${encodeURIComponent(resourceId!)}`),
  });
}
