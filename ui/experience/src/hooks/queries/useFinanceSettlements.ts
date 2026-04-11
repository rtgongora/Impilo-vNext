/**
 * MusheX-backed settlement operations proxied by Experience BFF (not /internal/v1/mushex/*).
 *
 * - POST /internal/v1/finance/settlements/run
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

export function useFinanceRunSettlement() {
  const qc = useQueryClient();
  return useMutation<FinanceSettlementJson, unknown, SettlementRunPayload>({
    mutationFn: (payload) => apiClient.post<FinanceSettlementJson>("/internal/v1/finance/settlements/run", payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "settlements"] });
    },
  });
}

export function useFinanceReleasePayouts() {
  const qc = useQueryClient();
  return useMutation<FinanceSettlementJson, unknown, string>({
    mutationFn: (settlementId) =>
      apiClient.post<FinanceSettlementJson>(
        `/internal/v1/finance/settlements/${encodeURIComponent(settlementId)}/release-payouts`,
      ),
    onSuccess: (_, settlementId) => {
      void qc.invalidateQueries({ queryKey: ["finance", "settlements", settlementId] });
    },
  });
}
