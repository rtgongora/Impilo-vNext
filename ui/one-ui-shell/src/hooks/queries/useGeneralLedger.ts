/**
 * Experience UI — Native general ledger (general-ledger-service via BFF).
 *
 * Proxies to `/internal/v1/erp/gl/**` (`ErpGlBffController`).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function q(params: Record<string, string | number | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

export function useGlAccounts() {
  return useQuery({
    queryKey: ["erp-gl", "accounts"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/gl/accounts"),
  });
}

export function useGlOpenPeriods() {
  return useQuery({
    queryKey: ["erp-gl", "periods-open"],
    queryFn: () => apiClient.get<unknown>("/internal/v1/erp/gl/periods/open"),
  });
}

export function useGlJournals(periodId?: string | null) {
  return useQuery({
    queryKey: ["erp-gl", "journals", periodId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/gl/journals${q({ period_id: periodId })}`),
    enabled: !!periodId,
  });
}

export function useGlTrialBalance(periodId?: string | null) {
  return useQuery({
    queryKey: ["erp-gl", "trial-balance", periodId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/gl/trial-balance${q({ period_id: periodId })}`),
    enabled: !!periodId,
  });
}

export function useGlIncomeStatement(periodId?: string | null) {
  return useQuery({
    queryKey: ["erp-gl", "income", periodId],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/erp/gl/financial-statements/income-statement${q({ period_id: periodId })}`,
      ),
    enabled: !!periodId,
  });
}

export function useGlBalanceSheet(periodId?: string | null) {
  return useQuery({
    queryKey: ["erp-gl", "balance-sheet", periodId],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/erp/gl/financial-statements/balance-sheet${q({ period_id: periodId })}`,
      ),
    enabled: !!periodId,
  });
}

export function useGlBudgets(fiscalYear: number) {
  return useQuery({
    queryKey: ["erp-gl", "budgets", fiscalYear],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/erp/gl/budgets${q({ fiscalYear })}`),
  });
}

export function useGlSeedAccounts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.post<unknown>("/internal/v1/erp/gl/accounts/seed"),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["erp-gl", "accounts"] });
    },
  });
}

export function useGlCreateJournal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/erp/gl/journals", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["erp-gl", "journals"] });
    },
  });
}

export function useGlPostJournal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) =>
      apiClient.post<unknown>(`/internal/v1/erp/gl/journals/${encodeURIComponent(entryId)}/post`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["erp-gl", "journals"] });
      qc.invalidateQueries({ queryKey: ["erp-gl", "trial-balance"] });
    },
  });
}
