/**
 * COSTA financial lifecycle via Experience BFF billing-workspace proxy.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceWorkspaceJson = unknown;

export function useFinanceBillingInvoice(invoiceId: string, enabled: boolean) {
  return useQuery<FinanceWorkspaceJson>({
    queryKey: ["finance", "billing-workspace", "invoice", invoiceId],
    queryFn: () =>
      apiClient.get<FinanceWorkspaceJson>(
        `/internal/v1/finance/billing-workspace/lifecycle/invoices/${encodeURIComponent(invoiceId)}`,
      ),
    enabled: enabled && invoiceId.trim().length > 0,
  });
}

export function useFinanceBillingCharges(billId: string, enabled: boolean) {
  const q = new URLSearchParams();
  if (billId) q.set("billId", billId);
  const suffix = q.toString();

  return useQuery<FinanceWorkspaceJson>({
    queryKey: ["finance", "billing-workspace", "charges", billId],
    queryFn: () =>
      apiClient.get<FinanceWorkspaceJson>(
        `/internal/v1/finance/billing-workspace/lifecycle/charges${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && billId.trim().length > 0,
  });
}
