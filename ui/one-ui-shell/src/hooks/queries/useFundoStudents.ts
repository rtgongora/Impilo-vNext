"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type ApiData<T = unknown> = { data: T };

/** A5 — admissions + student registry. */

export function useFundoApplications(status?: string, programId?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (status) qs.set("status", status);
  if (programId) qs.set("programId", programId);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "applications", status ?? "", programId ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/admissions/applications?${qs.toString()}`),
  });
}

export function useSubmitFundoApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/admissions/applications", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "applications"] }),
  });
}

export function useDecideFundoApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ applicationId, body }: { applicationId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/admissions/applications/${encodeURIComponent(applicationId)}/decision`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "applications"] }),
  });
}

export function useAdmitFundoStudent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/students", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fundo", "applications"] });
      qc.invalidateQueries({ queryKey: ["fundo", "students"] });
    },
  });
}

export function useFundoStudents(programId?: string, limit = 100) {
  const qs = new URLSearchParams();
  if (programId) qs.set("programId", programId);
  qs.set("limit", String(limit));
  return useQuery<ApiData<{ items?: Array<Record<string, unknown>> }>>({
    queryKey: ["fundo", "students", programId ?? "", limit],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/students?${qs.toString()}`),
  });
}

/** A6 — registration / placement / graduation + academic record. */

export function useFundoAcademicRecord(studentId?: string) {
  return useQuery<ApiData<Record<string, unknown>>>({
    queryKey: ["fundo", "academic-record", studentId],
    enabled: Boolean(studentId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/students/${encodeURIComponent(studentId!)}/academic-record`),
  });
}

export function useRegisterFundoCourse(studentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/students/${encodeURIComponent(studentId)}/registrations`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic-record", studentId] }),
  });
}

export function useCreateFundoPlacement(studentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/students/${encodeURIComponent(studentId)}/placements`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic-record", studentId] }),
  });
}

export function useSignoffFundoPlacement(studentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ placementId, body }: { placementId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/placements/${encodeURIComponent(placementId)}/signoff`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic-record", studentId] }),
  });
}

export function useGraduateFundoStudent(studentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/internal/v1/learning/v11/students/${encodeURIComponent(studentId)}/graduate`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fundo", "academic-record", studentId] }),
  });
}
