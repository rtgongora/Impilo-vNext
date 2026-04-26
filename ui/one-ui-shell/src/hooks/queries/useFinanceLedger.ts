/**
 * Finance ledger via Experience BFF.
 *
 * - GET /internal/v1/finance/ledger?intentId=...
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceLedgerJson = unknown;

export function useFinanceLedger(params: { intentId?: string }, enabled: boolean) {
  const query = new URLSearchParams();
  if (params.intentId) query.set("intentId", params.intentId);
  const suffix = query.toString();

  return useQuery<FinanceLedgerJson>({
    queryKey: ["finance", "ledger", params.intentId ?? ""],
    queryFn: () => apiClient.get<FinanceLedgerJson>(`/internal/v1/finance/ledger${suffix ? `?${suffix}` : ""}`),
    enabled,
  });
}
