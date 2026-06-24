/**
 * COSTA financial lifecycle via Experience BFF billing-workspace proxy.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceWorkspaceJson = unknown;

/**
 * COSTA revenue summary for a facility/period — returns totalRevenue, encounterCount,
 * byPayerType (payer mix), byDepartment, monthlyTrend. Real aggregation, already
 * proxied by FinancialReportsBffController.
 */
export function useFinanceRevenueSummary(
  facilityId: string | null | undefined,
  year: number,
  month: number,
) {
  const q = new URLSearchParams();
  if (facilityId) q.set("facility_id", facilityId);
  q.set("year", String(year));
  q.set("month", String(month));
  return useQuery<FinanceWorkspaceJson>({
    queryKey: ["finance", "revenue-summary", facilityId ?? null, year, month],
    queryFn: () =>
      apiClient.get<FinanceWorkspaceJson>(`/internal/v1/finance/reports/revenue-summary?${q.toString()}`),
    enabled: !!facilityId,
  });
}

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

export function useFinanceBillingInvoicesForEncounter(encounterId: string, enabled: boolean) {
  const q = new URLSearchParams();
  if (encounterId) q.set("encounterId", encounterId);
  const suffix = q.toString();

  return useQuery<FinanceWorkspaceJson>({
    queryKey: ["finance", "billing-workspace", "invoices-by-encounter", encounterId],
    queryFn: () =>
      apiClient.get<FinanceWorkspaceJson>(
        `/internal/v1/finance/billing-workspace/lifecycle/invoices${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && encounterId.trim().length > 0,
  });
}

export function useFinanceBillingSubsidiesForEncounter(encounterId: string, enabled: boolean) {
  const q = new URLSearchParams();
  if (encounterId) q.set("encounterId", encounterId);
  const suffix = q.toString();

  return useQuery<FinanceWorkspaceJson>({
    queryKey: ["finance", "billing-workspace", "subsidies-by-encounter", encounterId],
    queryFn: () =>
      apiClient.get<FinanceWorkspaceJson>(
        `/internal/v1/finance/billing-workspace/lifecycle/subsidies${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && encounterId.trim().length > 0,
  });
}
