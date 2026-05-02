/**
 * VITO Biometric Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Wraps the Experience BFF biometric endpoints which bridge to VITO.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const BASE_PATH = "/internal/v1/identity/biometric/vito";

// ── Interfaces ──────────────────────────────────────────────────────

export interface BiometricTemplate {
  position: string;
  type: string;
  format: string;
  version: string;
  template: string;
}

export interface BiometricProfile {
  healthId: string;
  enrollmentDate: string;
  lastVerifiedDate?: string;
  templates: BiometricTemplate[];
  status: "ENROLLED" | "NOT_ENROLLED" | "EXCEPTION";
  exceptionReason?: string;
}

export interface BiometricEnrollRequest {
  healthId: string;
  templates: BiometricTemplate[];
}

export interface BiometricVerifyRequest {
  healthId: string;
  template: BiometricTemplate;
}

export interface BiometricIdentifyRequest {
  template: BiometricTemplate;
  topN?: number;
}

export interface BiometricDedupAssistRequest {
  healthId: string;
  candidates: string[];
}

export interface BiometricExceptionRequest {
  healthId: string;
  reason: string;
  evidenceReference?: string;
}

// ── Queries ─────────────────────────────────────────────────────────

export function useBiometricTemplates(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "biometric", "templates", healthId],
    queryFn: () =>
      apiClient.get<ApiResponse<BiometricTemplate[]>>(`${BASE_PATH}/clients/${healthId}/templates`),
    enabled: !!healthId,
  });
}

export function useBiometricProfile(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "biometric", "profile", healthId],
    queryFn: () =>
      apiClient.get<ApiResponse<BiometricProfile>>(`${BASE_PATH}/clients/${healthId}/profile`),
    enabled: !!healthId,
  });
}

// ── Mutations ───────────────────────────────────────────────────────

export function useBiometricEnroll() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: BiometricEnrollRequest) =>
      apiClient.post<ApiResponse<void>>(`${BASE_PATH}/enroll`, body),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "biometric", "profile", variables.healthId] });
      void queryClient.invalidateQueries({ queryKey: ["vito", "biometric", "templates", variables.healthId] });
    },
  });
}

export function useBiometricVerify() {
  return useMutation({
    mutationFn: (body: BiometricVerifyRequest) =>
      apiClient.post<ApiResponse<{ verified: boolean; score: number }>>(`${BASE_PATH}/verify`, body),
  });
}

export function useBiometricIdentify() {
  return useMutation({
    mutationFn: (body: BiometricIdentifyRequest) =>
      apiClient.post<ApiResponse<{ healthId: string; score: number }[]>>(`${BASE_PATH}/identify`, body),
  });
}

export function useBiometricDedupAssist() {
  return useMutation({
    mutationFn: (body: BiometricDedupAssistRequest) =>
      apiClient.post<ApiResponse<void>>(`${BASE_PATH}/dedup-assist`, body),
  });
}

export function useBiometricException() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: BiometricExceptionRequest) =>
      apiClient.post<ApiResponse<void>>(`${BASE_PATH}/exception`, body),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "biometric", "profile", variables.healthId] });
    },
  });
}
