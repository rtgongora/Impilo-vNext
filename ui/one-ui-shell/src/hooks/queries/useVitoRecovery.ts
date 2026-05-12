/**
 * VITO Recovery Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Direct Envoy routes to VITO /api/v1/recovery/... for secure handover
 * and Secure Health State (SHS) management.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Interfaces ──────────────────────────────────────────────────────

export interface HandoverRequest {
  healthId: string;
  oldCardId: string;
  reason: string;
  newPublicKey: string;
}

export interface SHSCreateResult {
  jwsCompact: string;
  expiresAt: string;
}

export interface SHSVerifyResult {
  valid: boolean;
  claims: Record<string, unknown>;
  issuedAt: string;
  issuer: string;
}

// ── Hooks ───────────────────────────────────────────────────────────

/** Perform secure handover of a digital identity after credential loss. */
export function useSecureHandover() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: HandoverRequest) =>
      apiClient.post<ApiResponse<{ success: boolean; newCardId: string }>>(
        "/api/v1/recovery/handover",
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["vito", "recovery"] });
      void queryClient.invalidateQueries({ queryKey: ["identity"] });
    },
  });
}

/** Create a signed Secure Health State (SHS) capsule. */
export function useCreateSHS() {
  return useMutation({
    mutationFn: (body: { healthId: string; fhirBundleJson: string }) =>
      apiClient.post<ApiResponse<SHSCreateResult>>(
        "/api/v1/recovery/shs/create",
        body,
      ),
  });
}

/** Verify a signed Secure Health State (SHS) capsule. */
export function useVerifySHS() {
  return useMutation({
    mutationFn: (body: { jwsCompact: string }) =>
      apiClient.post<ApiResponse<SHSVerifyResult>>(
        "/api/v1/recovery/shs/verify",
        body,
      ),
  });
}
