"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { FundoSubjectRef } from "@/hooks/queries/useFundoLms";

type ApiData<T = unknown> = { data: T };

function q(params: Record<string, string | number | boolean | undefined>) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === "") return;
    qs.set(k, String(v));
  });
  const out = qs.toString();
  return out ? `?${out}` : "";
}

export function useFundoStudioDashboard() {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "studio", "dashboard"],
    queryFn: () => apiClient.get("/internal/v1/learning/v11/studio/dashboard"),
  });
}

export function useFundoStudioReadiness(courseId?: string) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "studio", "readiness", courseId],
    enabled: Boolean(courseId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/studio/courses/${encodeURIComponent(courseId!)}/readiness`),
  });
}

export function useFundoLibraryResources(limit = 50) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "library", "resources", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/library/resources?limit=${limit}`),
  });
}

export function useFundoMediaAssets(limit = 50) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "media", "assets", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/media/assets?limit=${limit}`),
  });
}

export function useFundoInteractiveActivities(courseId?: string, lessonId?: string, limit = 50) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "interactive", "activities", courseId, lessonId, limit],
    queryFn: () =>
      apiClient.get(`/internal/v1/learning/v11/interactive/activities${q({ courseId, lessonId, limit })}`),
  });
}

export function useFundoNotifications(subject?: FundoSubjectRef, limit = 50) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "notifications", subject, limit],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/notifications?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}&limit=${limit}`,
      ),
  });
}

export function useCreateFundoCourse() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/catalog", body),
  });
}

export function useUpdateFundoCourse(courseId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.put(`/internal/v1/learning/v11/catalog/${encodeURIComponent(courseId)}`, body),
  });
}

export function useCreateFundoModule(courseId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/courses/${encodeURIComponent(courseId)}/modules`, body),
  });
}

export function useCreateFundoLesson(moduleId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/modules/${encodeURIComponent(moduleId)}/lessons`, body),
  });
}

export function useGenerateFundoAiDraft() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/ai/generate", body),
  });
}

export function useNompiloAssist() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/nompilo/assist", body),
  });
}

export function useCreateLibraryResource() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/learning/v11/library/resources", body),
  });
}

export function useLinkLibraryResource(resourceId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/library/resources/${encodeURIComponent(resourceId)}/links`, body),
  });
}

export function useCreateMediaAsset() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/media/assets", body),
  });
}

export function useCreateInteractiveActivity() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/learning/v11/interactive/activities", body),
  });
}

export function useCreateFundoAssessment() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/assessments", body),
  });
}

export function useSubmitInteractiveResponse(activityId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/interactive/activities/${encodeURIComponent(activityId)}/responses`, body),
  });
}

export function useCreateCohort() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/cohorts", body),
  });
}

export function useCreateSession() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/sessions", body),
  });
}

export function useScheduleLearningNotification() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/notifications", body),
  });
}
