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
    queryFn: () =>
      apiClient.post<ApiResponse<ClientIdentity>>("/internal/v1/identity/patient/resolve", {
        healthId,
      }),
    enabled: !!healthId,
  });
}

export function useClientSearch(query: string) {
  return useQuery({
    queryKey: ["identity", "search", query],
    queryFn: () => apiClient.get<ApiResponse<ClientIdentity[]>>(`/internal/v1/identity/search?query=${encodeURIComponent(query)}`),
    enabled: query.length >= 3,
  });
}

export function useRegisterClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { givenName: string; familyName: string; dateOfBirth: string; sex: string }) =>
      apiClient.post<ApiResponse<ClientIdentity>>("/internal/v1/identity/patient/register", body),
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

// ── Provisional Health ID ───────────────────────────────────────────────────
//
// The real VITO-issued provisional identity for a TEMPORARY-tier citizen. The BFF either
// returns a genuine provisional Health ID with a server-computed expiry, or an honest
// PENDING state (id still being issued). The UI must render exactly what the server says —
// never fabricate an id or a client-side expiry.

export interface ProvisionalHealthId {
  /** The VITO-issued provisional Health ID, or null while PENDING. */
  provisionalHealthId: string | null;
  /** TEMPORARY once issued; PENDING while the id is still being provisioned. */
  status: "TEMPORARY" | "PENDING";
  assuranceLevel: string;
  /** Server-computed expiry (ISO 8601), or null while PENDING. */
  expiresAt: string | null;
}

/**
 * Request (idempotently) the caller's provisional Health ID. Identity is bound server-side
 * from trust headers — no id is sent. Enabled only for TEMPORARY-tier citizens.
 */
export function useProvisionalHealthId(enabled = true) {
  return useQuery({
    queryKey: ["identity", "health-id", "provisional"],
    queryFn: () =>
      apiClient.post<ApiResponse<ProvisionalHealthId>>("/internal/v1/identity/health-id/provisional", {}),
    enabled,
    retry: 1,
  });
}

export function useResolveIdentity() {
  return useMutation({
    mutationFn: (body: { aliasType: string; aliasValue: string }) =>
      apiClient.post<ApiResponse<{ healthId: string; resolved: boolean }>>(
        "/internal/v1/vito/identity/resolve",
        body,
      ),
  });
}

export function useRotateAlias() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { healthId: string; aliasType: string; newValue: string }) =>
      apiClient.post<ApiResponse<void>>("/internal/v1/vito/identity/rotate", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}
