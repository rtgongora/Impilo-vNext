/**
 * MusheX payer / ops surfaces via Experience BFF.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinancePayerOpsJson = unknown;

export function usePayerOpsPaymentIntent(intentId: string | undefined) {
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "intent", intentId],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(String(intentId))}`,
      ),
    enabled: Boolean(intentId),
  });
}

export function usePayerOpsReceipts(intentId: string | undefined) {
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "receipts", intentId],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(String(intentId))}/receipts`,
      ),
    enabled: Boolean(intentId),
  });
}

export function usePayerOpsCancelIntent() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, string>({
    mutationFn: (intentId) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(intentId)}/cancel`,
      ),
    onSuccess: (_, intentId) => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "intent", intentId] });
    },
  });
}

export function usePayerOpsIssueRemittanceSlip() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, string>({
    mutationFn: (intentId) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(intentId)}/issue-remittance-slip`,
      ),
    onSuccess: (_, intentId) => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "intent", intentId] });
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "receipts", intentId] });
    },
  });
}

export function usePayerOpsClaimRemittance() {
  return useMutation<FinancePayerOpsJson, unknown, unknown>({
    mutationFn: (body: unknown) =>
      apiClient.post<FinancePayerOpsJson>("/internal/v1/finance/payer-ops/remittance/claim", body),
  });
}

export function usePayerOpsAdapters(enabled: boolean) {
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "adapters"],
    queryFn: () => apiClient.get<FinancePayerOpsJson>("/internal/v1/finance/payer-ops/adapters"),
    enabled,
  });
}

export function usePayerOpsFraudFlags(query: Record<string, string>, enabled: boolean) {
  const sp = new URLSearchParams(query);
  const q = sp.toString();
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "fraud-flags", q],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(`/internal/v1/finance/payer-ops/fraud-flags${q ? `?${q}` : ""}`),
    enabled,
  });
}

export function usePayerOpsReviews(query: Record<string, string>, enabled: boolean) {
  const sp = new URLSearchParams(query);
  const q = sp.toString();
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "ops-reviews", q],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(`/internal/v1/finance/payer-ops/ops-reviews${q ? `?${q}` : ""}`),
    enabled,
  });
}

export function usePayerOpsReviewApprove() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, { reviewId: string; body?: unknown }>({
    mutationFn: ({ reviewId, body }) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/ops-reviews/${encodeURIComponent(reviewId)}/approve`,
        body ?? {},
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "ops-reviews"] });
    },
  });
}

export function usePayerOpsReviewReject() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, { reviewId: string; body?: unknown }>({
    mutationFn: ({ reviewId, body }) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/ops-reviews/${encodeURIComponent(reviewId)}/reject`,
        body ?? {},
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "ops-reviews"] });
    },
  });
}
