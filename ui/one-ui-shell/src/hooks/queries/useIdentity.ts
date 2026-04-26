/**
 * Identity & Trust Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Queries the Experience BFF identity endpoints which bridge to
 * VITO (client registry) and VARAPI (provider registry) via
 * IdentityServicesController and ProviderActivationController.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Client / Health ID ──────────────────────────────────────────────

export interface ClientIdentity {
  healthId: string;
  impiloId: string;
  status: string;
  assuranceLevel: string;
  demographics?: { givenName?: string; familyName?: string; dateOfBirth?: string; sex?: string };
}

export function useClientIdentity(healthId: string | undefined) {
  return useQuery({
    queryKey: ["identity", "client", healthId],
    queryFn: () => apiClient.get<ApiResponse<ClientIdentity>>(`/internal/v1/identity/client/${healthId}`),
    enabled: !!healthId,
  });
}

export function useClientSearch(query: string) {
  return useQuery({
    queryKey: ["identity", "search", query],
    queryFn: () => apiClient.get<ApiResponse<ClientIdentity[]>>(`/internal/v1/identity/search?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 3,
  });
}

export function useRegisterClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { givenName: string; familyName: string; dateOfBirth: string; sex: string }) =>
      apiClient.post<ApiResponse<ClientIdentity>>("/internal/v1/identity/register", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}

// ── Provider ID ─────────────────────────────────────────────────────

export interface ProviderRecord {
  providerId: string;
  displayName: string;
  cadre: string;
  registrationNumber: string;
  status: string;
  licensureExpiry?: string;
}

/** List Provider IDs linked to a Health ID (for Provider Activation flow). */
export function useProvidersByActor(actorId: string | undefined) {
  return useQuery({
    queryKey: ["identity", "providers", actorId],
    queryFn: () => apiClient.get<ApiResponse<ProviderRecord[]>>(`/internal/v1/identity/providers?actorId=${encodeURIComponent(actorId!)}`),
    enabled: !!actorId,
  });
}

export function useProviderDetail(providerId: string | undefined) {
  return useQuery({
    queryKey: ["identity", "provider", providerId],
    queryFn: () => apiClient.get<ApiResponse<ProviderRecord>>(`/internal/v1/identity/provider/${providerId}`),
    enabled: !!providerId,
  });
}

// ── Identity Assurance ──────────────────────────────────────────────

export interface AssuranceStatus {
  assuranceState: string;
  reason: string;
  permissionsAvailable: string[];
  upgradeRequirements: string[];
  eligibleUpgradePathways: { id: string; label: string; href: string }[];
  nextBestStep: string;
}

export function useAssuranceStatus() {
  return useQuery({
    queryKey: ["identity", "assurance"],
    queryFn: () => apiClient.get<ApiResponse<AssuranceStatus>>("/internal/v1/identity/assurance/status"),
  });
}

export function useRequestAssuranceUpgrade() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { method: string }) =>
      apiClient.post<ApiResponse<{ status: string; nextSteps: string[] }>>("/internal/v1/identity/assurance/upgrade/request", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["identity", "assurance"] });
    },
  });
}
