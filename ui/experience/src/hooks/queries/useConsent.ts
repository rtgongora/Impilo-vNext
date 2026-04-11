/**
 * Policy & Consent Hooks — Health OS §2 (Policy & Consent Services)
 *
 * Queries consent directives and policy decisions via the Experience BFF,
 * which bridges to tshepo-consent-service.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ConsentDirective {
  id: string;
  subjectId: string;
  scope: string;
  status: "ACTIVE" | "REVOKED" | "EXPIRED" | "DRAFT";
  purposeOfUse: string;
  grantedTo?: string;
  validFrom: string;
  validTo?: string;
  createdAt: string;
}

export interface ConsentDecision {
  allowed: boolean;
  scope: string;
  basis: string;
  evaluatedAt: string;
}

/** List consent directives for the current user (as subject). */
export function useMyConsents() {
  return useQuery({
    queryKey: ["consent", "mine"],
    queryFn: () => apiClient.get<ApiResponse<ConsentDirective[]>>("/internal/v1/consent/mine"),
  });
}

/** List consent directives where the current user is the grantee (provider view). */
export function useGrantedConsents(subjectId: string | undefined) {
  return useQuery({
    queryKey: ["consent", "granted", subjectId],
    queryFn: () => apiClient.get<ApiResponse<ConsentDirective[]>>(`/internal/v1/consent/subject/${subjectId}`),
    enabled: !!subjectId,
  });
}

/** Check if guidance personalization consent is granted. */
export function useGuidanceConsentStatus() {
  return useQuery({
    queryKey: ["consent", "guidance"],
    queryFn: () => apiClient.get<{ data: { guidanceConsent: boolean } }>("/internal/v1/guidance/consent-status"),
  });
}

/** Create a new consent directive. */
export function useCreateConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { scope: string; purposeOfUse: string; grantedTo: string; validFrom: string; validTo?: string }) =>
      apiClient.post<ApiResponse<ConsentDirective>>("/internal/v1/consent/create", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["consent"] });
    },
  });
}

/** Revoke a consent directive. */
export function useRevokeConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (consentId: string) =>
      apiClient.post<ApiResponse<{ status: string }>>(`/internal/v1/consent/${consentId}/revoke`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["consent"] });
    },
  });
}
