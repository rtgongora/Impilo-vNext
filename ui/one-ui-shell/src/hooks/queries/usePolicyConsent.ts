/**
 * Experience UI — Policy Consent & Account Deletion Hooks
 *
 * Dedicated TanStack Query hooks for the Privacy Policy / Terms of Use
 * consent pipeline and account deletion lifecycle. These wire the
 * frontend to the PolicyConsentController and AccountDeletionController
 * endpoints in the Experience BFF.
 *
 * Separate from useConsent.ts which handles TSHEPO clinical consent directives.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";

// ── Types ────────────────────────────────────────────────────────

export interface PolicyConsentResource {
  id: string;
  type: "policy_consent";
  attributes: {
    policyType: string;
    policyVersion: string;
    accepted: boolean;
    acceptedAt: string;
    revokedAt: string;
    channel?: string;
    deviceType?: string;
  };
}

export interface PolicyConsentHistoryResource {
  id: string;
  type: "policy_consent" | "policy_consent_history";
  attributes: {
    policyType: string;
    policyVersion: string;
    accepted: boolean;
    /** Mvumo lifecycle state: GRANTED | WITHDRAWN | SUPERSEDED. */
    state?: string;
    acceptedAt: string;
    revokedAt: string;
    channel?: string;
    deviceType?: string;
    createdAt?: string;
  };
}

export interface DeletionRequestResource {
  id: string;
  type: "account_deletion_request";
  attributes: {
    status: string;
    requestedAt: string;
    processedAt: string;
    completedAt: string;
    message?: string;
  };
}

// ── Consent Status ───────────────────────────────────────────────

/** Fetch current consent status for the authenticated user. */
export function usePolicyConsentStatus(version?: string) {
  const v = version ?? CURRENT_CONSENT_VERSION;
  return useQuery<ApiResponse<PolicyConsentResource[]>>({
    queryKey: ["policy-consent-status", v],
    queryFn: () =>
      apiClient.get<ApiResponse<PolicyConsentResource[]>>(
        `/internal/v1/consent/status?version=${v}`
      ),
  });
}

/** Fetch full consent history for the authenticated user. */
export function usePolicyConsentHistory() {
  return useQuery<ApiResponse<PolicyConsentHistoryResource[]>>({
    queryKey: ["policy-consent-history"],
    queryFn: () =>
      apiClient.get<ApiResponse<PolicyConsentHistoryResource[]>>(
        "/internal/v1/consent/history"
      ),
  });
}

// ── Accept Consent ───────────────────────────────────────────────

interface AcceptConsentPayload {
  version: string;
  privacyPolicyAccepted: boolean;
  termsOfUseAccepted: boolean;
  channel?: string;
  deviceType?: string;
}

/** Accept Privacy Policy and/or Terms of Use. */
export function useAcceptPolicyConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AcceptConsentPayload) =>
      apiClient.post("/internal/v1/consent/accept", payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["policy-consent-status"] });
    },
  });
}

// ── Revoke Consent ───────────────────────────────────────────────

/** Revoke a specific policy consent. */
export function useRevokePolicyConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (policyType: string) =>
      apiClient.post("/internal/v1/consent/revoke", { policyType }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["policy-consent-status"] });
    },
  });
}

// ── Account Deletion ─────────────────────────────────────────────

/** Fetch current account deletion request status. */
export function useDeletionRequestStatus() {
  return useQuery<ApiResponse<DeletionRequestResource | null>>({
    queryKey: ["account-deletion-status"],
    queryFn: () =>
      apiClient.get<ApiResponse<DeletionRequestResource | null>>(
        "/internal/v1/account/delete/status"
      ),
  });
}

/** Submit an account deletion request. */
export function useRequestAccountDeletion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: { reason: string }) =>
      apiClient.post("/internal/v1/account/delete", payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["account-deletion-status"] });
    },
  });
}

/** Cancel a pending account deletion request. */
export function useCancelAccountDeletion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post("/internal/v1/account/delete/cancel", {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["account-deletion-status"] });
    },
  });
}
