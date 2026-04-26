/**
 * Caregiver Linkage Hooks — Health OS §4, §5
 *
 * "One human being may lawfully hold multiple other identifiers linked to role,
 *  organization, function, domain object, or transaction."
 *
 * Manages caregiver ↔ subject linkages: a caregiver can view/manage health
 * data for their dependants within their granted scope.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface CaregiverLinkage {
  id: string;
  caregiverHealthId: string;
  subjectHealthId: string;
  subjectDisplayName?: string;
  relationship: string;
  linkageType: "FAMILY" | "PROFESSIONAL" | "LEGAL" | "COMMUNITY";
  status: "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED";
  grantedScopes: string[];
  consentRef?: string;
  grantedBy: string;
  grantedAt: string;
  expiresAt?: string;
}

/** Linkages where the current user IS the caregiver (my dependants). */
export function useMyCaregivingLinkages() {
  return useQuery({
    queryKey: ["caregiver-linkages", "mine"],
    queryFn: () =>
      apiClient.get<ApiResponse<CaregiverLinkage[]>>("/internal/v1/caregiving/my-linkages"),
  });
}

/** Linkages where the current user IS the subject (who cares for me). */
export function useMyCaregivers() {
  return useQuery({
    queryKey: ["caregiver-linkages", "for-me"],
    queryFn: () =>
      apiClient.get<ApiResponse<CaregiverLinkage[]>>("/internal/v1/caregiving/my-caregivers"),
  });
}

/** Create a new caregiver linkage (delegate care). */
export function useCreateCaregiverLinkage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      subjectHealthId: string;
      relationship: string;
      linkageType: string;
      grantedScopes: string[];
      consentRef?: string;
      expiresAt?: string;
    }) => apiClient.post<ApiResponse<CaregiverLinkage>>("/internal/v1/caregiving/linkages", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["caregiver-linkages"] });
    },
  });
}

/** Revoke a caregiver linkage. */
export function useRevokeCaregiverLinkage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { linkageId: string; reason: string }) =>
      apiClient.post<ApiResponse<{ status: string }>>(`/internal/v1/caregiving/linkages/${body.linkageId}/revoke`, { reason: body.reason }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["caregiver-linkages"] });
    },
  });
}
