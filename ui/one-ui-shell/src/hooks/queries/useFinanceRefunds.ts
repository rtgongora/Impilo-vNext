/**
 * MusheX refund ops via Experience BFF.
 *
 * - GET  /internal/v1/finance/refunds/payment-intents/{intentId}
 * - POST /internal/v1/finance/refunds/payment-intents/{intentId}/refund
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceRefundJson = unknown;

export function useRefundPaymentIntent(intentId: string | undefined) {
  return useQuery<FinanceRefundJson>({
    queryKey: ["finance", "refunds", "intent", intentId],
    queryFn: () =>
      apiClient.get<FinanceRefundJson>(
        `/internal/v1/finance/refunds/payment-intents/${encodeURIComponent(String(intentId))}`,
      ),
    enabled: Boolean(intentId),
  });
}

export function useRefundCreate() {
  const qc = useQueryClient();
  return useMutation<FinanceRefundJson, unknown, { intentId: string; body: unknown }>({
    mutationFn: ({ intentId, body }) =>
      apiClient.post<FinanceRefundJson>(
        `/internal/v1/finance/refunds/payment-intents/${encodeURIComponent(intentId)}/refund`,
        body,
      ),
    onSuccess: (_, v) => {
      void qc.invalidateQueries({ queryKey: ["finance", "refunds", "intent", v.intentId] });
    },
  });
}
