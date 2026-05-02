"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "../../lib/api-client";

// ── Interfaces ──────────────────────────────────────────────────────

export interface PatientShare {
  id: string;
  healthId: string;
  status: "PENDING" | "ACTIVE" | "REVOKED" | "EXPIRED";
  purpose: string;
  recipientId: string;
  recipientName: string;
  createdAt: string;
  expiresAt: string;
}

export interface ShareContribution {
  id: string;
  shareId: string;
  contributorId: string;
  contributorName: string;
  type: string;
  contentSummary: string;
  timestamp: string;
}

export interface ShareWorkspace {
  sessionToken: string;
  healthId: string;
  patientName: string;
  activeShares: PatientShare[];
  recentContributions: ShareContribution[];
}

export interface CollaborationCouncil {
  id: string;
  name: string;
  type: string;
  tenantId: string;
}

// ── Authenticated Group (Direct Envoy Routes) ────────────────────────

export function usePatientShares(healthId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "patient-shares", healthId],
    queryFn: () =>
      apiClient.get<ApiResponse<PatientShare[]>>(`/api/v1/clients/${healthId}/patient-shares`),
    enabled: !!healthId,
  });
}

export function useShareContributions(healthId: string | undefined, shareRequestId: string | undefined) {
  return useQuery({
    queryKey: ["vito", "patient-shares", healthId, shareRequestId, "contributions"],
    queryFn: () =>
      apiClient.get<ApiResponse<ShareContribution[]>>(
        `/api/v1/clients/${healthId}/patient-shares/${shareRequestId}/contributions`
      ),
    enabled: !!healthId && !!shareRequestId,
  });
}

export function useCreatePatientShare() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, body }: { healthId: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<PatientShare>>(`/api/v1/clients/${healthId}/patient-shares`, body),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "patient-shares", variables.healthId] });
    },
  });
}

export function useRevokePatientShare() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, shareRequestId }: { healthId: string; shareRequestId: string }) =>
      apiClient.post<ApiResponse<void>>(
        `/api/v1/clients/${healthId}/patient-shares/${shareRequestId}/revoke`,
        {}
      ),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "patient-shares", variables.healthId] });
    },
  });
}

// ── Public Group (BFF Routes) ──────────────────────────────────────────

export function useValidatePublicShare() {
  return useMutation({
    mutationFn: (body: { shareToken: string }) =>
      apiClient.post<ApiResponse<{ sessionToken: string; requirements: string[] }>>(
        "/internal/v1/vito/public/patient-shares/validate",
        body
      ),
  });
}

export function useVerifyShareOtp() {
  return useMutation({
    mutationFn: (body: { sessionToken: string; otp: string }) =>
      apiClient.post<ApiResponse<{ verified: boolean }>>(
        "/internal/v1/vito/public/patient-shares/verify-otp",
        body
      ),
  });
}

export function useVerifyShareStepUp() {
  return useMutation({
    mutationFn: ({ sessionToken, body }: { sessionToken: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<{ verified: boolean }>>(
        "/internal/v1/vito/public/patient-shares/verify-step-up",
        body,
        {
          extraHeaders: { "X-Patient-Share-Session": sessionToken },
        }
      ),
  });
}

export function useCompleteShareIdentity() {
  return useMutation({
    mutationFn: ({ sessionToken, body }: { sessionToken: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<{ success: boolean }>>(
        "/internal/v1/vito/public/patient-shares/complete-identity",
        body,
        {
          extraHeaders: { "X-Patient-Share-Session": sessionToken },
        }
      ),
  });
}

export function useShareWorkspace(sessionToken: string | undefined) {
  return useQuery({
    queryKey: ["vito", "patient-shares", "public", "workspace", sessionToken],
    queryFn: () =>
      apiClient.get<ApiResponse<ShareWorkspace>>("/internal/v1/vito/public/patient-shares/workspace", {
        extraHeaders: { "X-Patient-Share-Session": sessionToken! },
      }),
    enabled: !!sessionToken,
  });
}

export function useAddShareContribution() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionToken, body }: { sessionToken: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<ShareContribution>>(
        "/internal/v1/vito/public/patient-shares/contributions",
        body,
        {
          extraHeaders: { "X-Patient-Share-Session": sessionToken },
        }
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "patient-shares", "public", "workspace"] });
    },
  });
}

export function useCollaborationCouncils(tenantId?: string) {
  return useQuery({
    queryKey: ["vito", "patient-shares", "public", "councils", tenantId],
    queryFn: () =>
      apiClient.get<ApiResponse<CollaborationCouncil[]>>(
        `/internal/v1/vito/public/patient-shares/councils${tenantId ? `?tenantId=${tenantId}` : ""}`
      ),
  });
}
