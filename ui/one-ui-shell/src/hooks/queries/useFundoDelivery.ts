"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** A1 — learning-delivery administration: facilitators, venues, cohort-facilitators, session delivery. */

export function useFundoFacilitators(kind?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (kind) qs.set("kind", kind);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "facilitators", kind ?? "all", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/facilitators?${qs.toString()}`),
  });
}

export function useCreateFundoFacilitator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/learning/v11/facilitators", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "facilitators"] }),
  });
}

export function useSetFundoFacilitatorStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiClient.post(`/internal/v1/learning/v11/facilitators/${encodeURIComponent(id)}/status`, { status }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "facilitators"] }),
  });
}

export function useFundoVenues(limit = 100) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "venues", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/venues?limit=${limit}`),
  });
}

export function useCreateFundoVenue() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/learning/v11/venues", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "venues"] }),
  });
}

export function useFundoCohortFacilitators(cohortId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "cohort-facilitators", cohortId],
    enabled: Boolean(cohortId),
    queryFn: () =>
      apiClient.get(`/internal/v1/learning/v11/cohorts/${encodeURIComponent(cohortId!)}/facilitators`),
  });
}

export function useAssignCohortFacilitator(cohortId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/cohorts/${encodeURIComponent(cohortId)}/facilitators`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "cohort-facilitators", cohortId] }),
  });
}

export function useAssignSessionDelivery() {
  return useMutation({
    mutationFn: ({ sessionId, body }: { sessionId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/sessions/${encodeURIComponent(sessionId)}/delivery`, body),
  });
}

/** A2 — attendance + check-in. */

export function useSessionAttendance(sessionId?: string) {
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "attendance", sessionId],
    enabled: Boolean(sessionId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/sessions/${encodeURIComponent(sessionId!)}/attendance`),
  });
}

export function useCreateCheckinToken(sessionId: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/sessions/${encodeURIComponent(sessionId)}/checkin-tokens`, body),
  });
}

export function useMarkAttendance(sessionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/sessions/${encodeURIComponent(sessionId)}/attendance`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "attendance", sessionId] }),
  });
}

export function useSelfCheckin(sessionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/sessions/${encodeURIComponent(sessionId)}/checkin`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "attendance", sessionId] }),
  });
}
