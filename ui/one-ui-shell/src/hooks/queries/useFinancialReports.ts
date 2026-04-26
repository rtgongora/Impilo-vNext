/**
 * Institutional financial reporting — COSTA via BFF.
 *
 * - GET  /internal/v1/finance/reports/*
 * - POST /internal/v1/finance/reports/close-period
 * - GET/POST/PUT /internal/v1/finance/budgets*
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export function unwrapFinanceData<T>(root: unknown): T {
  if (root && typeof root === "object" && root !== null && "data" in root) {
    return (root as { data: T }).data;
  }
  return root as T;
}

function q(params: Record<string, string | number | undefined>): string {
  const u = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== "") u.set(k, String(v));
  }
  const s = u.toString();
  return s ? `?${s}` : "";
}

export function useRevenueSummary(facilityId: string | undefined, year: number, month: number) {
  return useQuery({
    queryKey: ["finance", "reports", "revenue", facilityId ?? "", year, month],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/finance/reports/revenue-summary${q({ facility_id: facilityId, year, month })}`,
      );
      return unwrapFinanceData<Record<string, unknown>>(raw);
    },
    enabled: year > 0 && month >= 1 && month <= 12,
  });
}

export function useClaimsExpenditure(payerId: string | undefined, year: number, month: number) {
  return useQuery({
    queryKey: ["finance", "reports", "claims-exp", payerId ?? "", year, month],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/finance/reports/claims-expenditure${q({ payer_id: payerId, year, month })}`,
      );
      return unwrapFinanceData<Record<string, unknown>>(raw);
    },
    enabled: year > 0 && month >= 1 && month <= 12,
  });
}

export function useProviderPayments(providerId: string | undefined, year: number, month: number) {
  return useQuery({
    queryKey: ["finance", "reports", "provider-payments", providerId ?? "", year, month],
    queryFn: async () => {
      if (!providerId) throw new Error("providerId required");
      const raw = await apiClient.get<unknown>(
        `/internal/v1/finance/reports/provider-payments${q({ provider_id: providerId, year, month })}`,
      );
      return unwrapFinanceData<Record<string, unknown>>(raw);
    },
    enabled: Boolean(providerId) && year > 0 && month >= 1 && month <= 12,
  });
}

export function useDebtAging(facilityId: string | undefined) {
  return useQuery({
    queryKey: ["finance", "reports", "debt-aging", facilityId ?? ""],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/finance/reports/debt-aging${q({ facility_id: facilityId })}`,
      );
      return unwrapFinanceData<Record<string, unknown>>(raw);
    },
  });
}

export function useBudgetVsActual(facilityId: string | undefined, year: number) {
  return useQuery({
    queryKey: ["finance", "reports", "budget-vs-actual", facilityId ?? "", year],
    queryFn: async () => {
      if (!facilityId) throw new Error("facilityId required");
      const raw = await apiClient.get<unknown>(
        `/internal/v1/finance/reports/budget-vs-actual${q({ facility_id: facilityId, year })}`,
      );
      return unwrapFinanceData<Record<string, unknown>>(raw);
    },
    enabled: Boolean(facilityId) && year > 0,
  });
}

export function useBudgetAllocations(facilityId: string | undefined, year: number) {
  return useQuery({
    queryKey: ["finance", "budgets", facilityId ?? "", year],
    queryFn: async () => {
      if (!facilityId) throw new Error("facilityId required");
      const raw = await apiClient.get<unknown>(`/internal/v1/finance/budgets${q({ facility_id: facilityId, year })}`);
      return unwrapFinanceData<unknown[]>(raw);
    },
    enabled: Boolean(facilityId) && year > 0,
  });
}

export type CreateBudgetPayload = {
  facilityId: string;
  departmentId?: string;
  budgetCategory: string;
  periodYear: number;
  allocatedAmount: number;
};

export function useCreateBudget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateBudgetPayload) =>
      apiClient.post<unknown>("/internal/v1/finance/budgets", {
        facilityId: body.facilityId,
        departmentId: body.departmentId,
        budgetCategory: body.budgetCategory,
        periodYear: body.periodYear,
        allocatedAmount: body.allocatedAmount,
      }),
    onSuccess: (_, v) => {
      void qc.invalidateQueries({ queryKey: ["finance", "budgets", v.facilityId, v.periodYear] });
      void qc.invalidateQueries({ queryKey: ["finance", "reports", "budget-vs-actual", v.facilityId] });
    },
  });
}

export function useRecordBudgetSpend() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; amount: number; facilityId: string; year: number }) =>
      apiClient.put<unknown>(`/internal/v1/finance/budgets/${args.id}/spend`, { amount: args.amount }),
    onSuccess: (_, v) => {
      void qc.invalidateQueries({ queryKey: ["finance", "budgets", v.facilityId, v.year] });
      void qc.invalidateQueries({ queryKey: ["finance", "reports", "budget-vs-actual", v.facilityId] });
    },
  });
}

export type ClosePeriodPayload = { periodType: string; year: number; month: number };

export function useClosePeriod() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ClosePeriodPayload) =>
      apiClient.post<unknown>("/internal/v1/finance/reports/close-period", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "reports"] });
    },
  });
}
