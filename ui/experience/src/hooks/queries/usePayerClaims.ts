/**
 * Payer claims via Experience BFF.
 *
 * - GET  /internal/v1/finance/payer-claims/{claimId}
 * - POST /internal/v1/finance/payer-claims/{claimId}/submit
 * - POST /internal/v1/finance/payer-claims/{claimId}/dispute
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type PayerClaimJson = unknown;

function buildQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== "") query.set(key, String(value));
  }
  const suffix = query.toString();
  return suffix ? `?${suffix}` : "";
}

export function usePayerClaims(params: { status?: string; insurerId?: string; page?: number; size?: number }, enabled = true) {
  return useQuery<PayerClaimJson>({
    queryKey: ["finance", "payer-claims", "list", params.status ?? "", params.insurerId ?? "", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<PayerClaimJson>(`/internal/v1/finance/payer-claims${buildQuery(params)}`),
    enabled,
  });
}

export function usePayerClaim(claimId: string | undefined) {
  return useQuery<PayerClaimJson>({
    queryKey: ["finance", "payer-claims", claimId],
    queryFn: () =>
      apiClient.get<PayerClaimJson>(
        `/internal/v1/finance/payer-claims/${encodeURIComponent(String(claimId))}`,
      ),
    enabled: Boolean(claimId),
  });
}

function invalidateClaim(qc: ReturnType<typeof useQueryClient>, claimId: string) {
  void qc.invalidateQueries({ queryKey: ["finance", "payer-claims", claimId] });
  void qc.invalidateQueries({ queryKey: ["finance", "payer-claims", "list"] });
}

export function useSubmitPayerClaim() {
  const qc = useQueryClient();
  return useMutation<PayerClaimJson, unknown, string>({
    mutationFn: (claimId) =>
      apiClient.post<PayerClaimJson>(
        `/internal/v1/finance/payer-claims/${encodeURIComponent(claimId)}/submit`,
      ),
    onSuccess: (_, claimId) => invalidateClaim(qc, claimId),
  });
}

export function useDisputePayerClaim() {
  const qc = useQueryClient();
  return useMutation<PayerClaimJson, unknown, { claimId: string; body: unknown }>({
    mutationFn: ({ claimId, body }) =>
      apiClient.post<PayerClaimJson>(
        `/internal/v1/finance/payer-claims/${encodeURIComponent(claimId)}/dispute`,
        body,
      ),
    onSuccess: (_, { claimId }) => invalidateClaim(qc, claimId),
  });
}
