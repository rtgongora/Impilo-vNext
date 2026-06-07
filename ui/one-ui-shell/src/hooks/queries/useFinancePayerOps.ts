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

export function usePayerOpsPaymentIntentsBySource(
  sourceType: string | undefined,
  sourceIds: string[] | undefined,
  enabled: boolean,
) {
  const ids = (sourceIds ?? []).filter((id) => id && id.trim().length > 0);
  const query = new URLSearchParams();
  if (sourceType) query.set("sourceType", sourceType);
  for (const id of ids) {
    query.append("sourceIds", id);
  }
  const suffix = query.toString();
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "intents-by-source", sourceType ?? null, ids.join(",")],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && Boolean(sourceType) && ids.length > 0,
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

/**
 * Phase 3 follow-on — read-only chronological list of attempts persisted for an intent.
 */
export function usePayerOpsAttempts(intentId: string | undefined) {
  return useQuery<FinancePayerOpsJson>({
    queryKey: ["finance", "payer-ops", "attempts", intentId],
    queryFn: () =>
      apiClient.get<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(String(intentId))}/attempts`,
      ),
    enabled: Boolean(intentId),
  });
}

export interface InitiateAttemptArgs {
  intentId: string;
  idempotencyKey?: string;
  reason?: string;
}

/**
 * Phase 3 — initiate a payment attempt for an existing intent. The MusheX safety gate
 * (config + adapter.liveCapable()) ensures no real money moves until operator opt-in and
 * provider readiness; SANDBOX is always permitted for governed preview settlement.
 */
export function usePayerOpsInitiateAttempt() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, InitiateAttemptArgs>({
    mutationFn: ({ intentId, idempotencyKey, reason }) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(intentId)}/attempts`,
        { idempotencyKey, reason },
        idempotencyKey ? { extraHeaders: { "X-Idempotency-Key": idempotencyKey } } : undefined,
      ),
    onSuccess: (_, args) => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "intent", args.intentId] });
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "attempts", args.intentId] });
    },
  });
}

export interface ReselectArgs {
  intentId: string;
  reason?: string;
}

/**
 * Phase 3 follow-on — re-run rail selection for an existing intent. No money moves;
 * only the persisted {@code metadata.rail_selection} changes and the previous value is
 * preserved on {@code metadata.rail_selection.history[]}.
 */
export function usePayerOpsReselect() {
  const qc = useQueryClient();
  return useMutation<FinancePayerOpsJson, unknown, ReselectArgs>({
    mutationFn: ({ intentId, reason }) =>
      apiClient.post<FinancePayerOpsJson>(
        `/internal/v1/finance/payer-ops/payment-intents/${encodeURIComponent(intentId)}/attempts/reselect`,
        { reason },
      ),
    onSuccess: (_, args) => {
      void qc.invalidateQueries({ queryKey: ["finance", "payer-ops", "intent", args.intentId] });
    },
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
