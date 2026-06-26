"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** C1 — learning-provider & academy registry (3 regulated kinds). */

export function useFundoProviders(kind?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (kind) qs.set("kind", kind);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "providers", kind ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/providers?${qs.toString()}`),
  });
}

export function useCreateFundoProvider() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/providers", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "providers"] }),
  });
}

export function useFundoProviderSpaces(providerId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "provider-spaces", providerId],
    enabled: Boolean(providerId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/providers/${encodeURIComponent(providerId!)}/spaces`),
  });
}

export function useCreateFundoSpace(providerId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/providers/${encodeURIComponent(providerId)}/spaces`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "provider-spaces", providerId] }),
  });
}

/** C2 — provider accreditation workflow. */

export function useFundoProviderApplications(status?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (status) qs.set("status", status);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "provider-applications", status ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/provider-applications?${qs.toString()}`),
  });
}

export function useSubmitProviderApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/provider-applications", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "provider-applications"] }),
  });
}

export function useDecideProviderApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ applicationId, body }: { applicationId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/provider-applications/${encodeURIComponent(applicationId)}/decision`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "provider-applications"] }),
  });
}

export function useAccreditProvider() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (applicationId: string) =>
      apiClient.post(`/internal/v1/learning/v11/provider-applications/${encodeURIComponent(applicationId)}/accredit`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fundo", "provider-applications"] });
      qc.invalidateQueries({ queryKey: ["fundo", "providers"] });
    },
  });
}

/** C3 — delegated space administration. */

export function useFundoSpaceSummary(spaceId?: string) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "space-summary", spaceId],
    enabled: Boolean(spaceId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/spaces/${encodeURIComponent(spaceId!)}/summary`),
  });
}

export function useFundoSpaceCourses(spaceId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "space-courses", spaceId],
    enabled: Boolean(spaceId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/spaces/${encodeURIComponent(spaceId!)}/courses`),
  });
}

export function useAssignCourseToSpace(spaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ courseId, learningSpaceId }: { courseId: string; learningSpaceId: string | null }) =>
      apiClient.post(`/internal/v1/learning/v11/courses/${encodeURIComponent(courseId)}/learning-space`, { learningSpaceId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "space-courses", spaceId] }),
  });
}
