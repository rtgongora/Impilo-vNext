"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** A3 — assignments / tasks + submissions + marking. */

export function useFundoAssignments(courseId?: string, cohortId?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (courseId) qs.set("courseId", courseId);
  if (cohortId) qs.set("cohortId", cohortId);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "assignments", courseId ?? "", cohortId ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/assignments?${qs.toString()}`),
  });
}

export function useCreateFundoAssignment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/assignments", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "assignments"] }),
  });
}

export function useSubmitFundoAssignment(assignmentId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/assignments/${encodeURIComponent(assignmentId)}/submissions`, body),
  });
}

export function useFundoAssignmentSubmissions(assignmentId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "assignment-submissions", assignmentId],
    enabled: Boolean(assignmentId),
    queryFn: () =>
      apiClient.get(`/internal/v1/learning/v11/assignments/${encodeURIComponent(assignmentId!)}/submissions`),
  });
}

export function useFundoMarkingQueue(cohortId?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (cohortId) qs.set("cohortId", cohortId);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "marking-queue", cohortId ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/marking-queue?${qs.toString()}`),
  });
}

export function useMarkSubmission() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ submissionId, body }: { submissionId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/submissions/${encodeURIComponent(submissionId)}/mark`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "marking-queue"] }),
  });
}
