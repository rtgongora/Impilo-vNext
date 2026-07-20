/**
 * MusheX-backed settlement operations proxied by Experience BFF (not /internal/v1/mushex/*).
 *
 * - POST /internal/v1/finance/settlements/run
 * - GET  /internal/v1/finance/settlements?intentId=...|intentIds=...
 * - GET  /internal/v1/finance/settlements/{settlementId}
 * - POST /internal/v1/finance/settlements/{settlementId}/release-payouts
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceSettlementJson = unknown;

export type SettlementRunPayload = {
  periodStart: string;
  periodEnd: string;
};

export function useFinanceSettlement(settlementId: string | undefined) {
  return useQuery<FinanceSettlementJson>({
    queryKey: ["finance", "settlements", settlementId],
    queryFn: () =>
      apiClient.get<FinanceSettlementJson>(
        `/internal/v1/finance/settlements/${encodeURIComponent(String(settlementId))}`,
      ),
    enabled: Boolean(settlementId),
  });
}

export function useFinanceSettlementsByIntentIds(intentIds: string[] | undefined, enabled: boolean) {
  const ids = (intentIds ?? []).filter((id) => id && id.trim().length > 0);
  const q = new URLSearchParams();
  for (const id of ids) {
    q.append("intentIds", id);
  }
  const suffix = q.toString();
  return useQuery<FinanceSettlementJson>({
    queryKey: ["finance", "settlements", "by-intent-ids", ids.join(",")],
    queryFn: () =>
      apiClient.get<FinanceSettlementJson>(
        `/internal/v1/finance/settlements${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && ids.length > 0,
  });
}

export function useFinanceRunSettlement() {
  const qc = useQueryClient();
  return useMutation<FinanceSettlementJson, unknown, SettlementRunPayload>({
    mutationFn: (payload) => apiClient.post<FinanceSettlementJson>("/internal/v1/finance/settlements/run", payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "settlements"] });
    },
  });
}

export type ReleasePayoutsInput = {
  settlementId: string;
  /**
   * Optional high-value step-up. When supplied, MusheX verifies the releasing
   * officer through the shared biometric seam before releasing (threshold-gated):
   * MATCH → release proceeds, NO_MATCH → rejected (4xx), UNAVAILABLE → falls back
   * to the existing step-up factor.
   */
  biometric?: { subjectRef: string; modality: string; probeBase64: string };
};

export function useFinanceReleasePayouts() {
  const qc = useQueryClient();
  return useMutation<FinanceSettlementJson, unknown, ReleasePayoutsInput>({
    mutationFn: ({ settlementId, biometric }) => {
      const url = `/internal/v1/finance/settlements/${encodeURIComponent(settlementId)}/release-payouts`;
      // No biometric → post with no body, preserving the existing release behaviour.
      return biometric
        ? apiClient.post<FinanceSettlementJson>(url, {
            biometricSubjectRef: biometric.subjectRef,
            biometricModality: biometric.modality,
            biometricProbeBase64: biometric.probeBase64,
          })
        : apiClient.post<FinanceSettlementJson>(url);
    },
    onSuccess: (_, { settlementId }) => {
      void qc.invalidateQueries({ queryKey: ["finance", "settlements", settlementId] });
    },
  });
}
