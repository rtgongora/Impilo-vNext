/**
 * Insurance-plan terms governance via the Experience BFF.
 *
 * - GET /internal/v1/finance/insurance-plans?status=PENDING_TERMS
 * - PUT /internal/v1/finance/insurance-plans/{planCode}
 *
 * COSTA saves a PENDING_TERMS placeholder when a coverage plan is first seen; it
 * never invents the billing split. Finance configures the real terms here, which
 * activates the plan so the coverage engine can start splitting bills against it.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type InsurancePlan = {
  id: number;
  insurerName: string;
  planCode: string;
  planName: string | null;
  coveragePct: number;
  copayPct: number;
  copayFixed: number | null;
  deductible: number;
  annualCap: number | null;
  status: string;
  effectiveFrom: string;
};

export type PlanTerms = {
  coveragePct: number;
  copayPct: number;
  copayFixed?: number | null;
  deductible?: number;
  annualCap?: number | null;
  planName?: string;
};

const KEY = ["finance", "insurance-plans"] as const;

export function useInsurancePlans(status?: string) {
  return useQuery<ApiResponse<InsurancePlan[]>>({
    queryKey: [...KEY, status ?? "ALL"],
    queryFn: () => {
      const qs = status ? `?status=${encodeURIComponent(status)}` : "";
      return apiClient.get<ApiResponse<InsurancePlan[]>>(
        `/internal/v1/finance/insurance-plans${qs}`,
      );
    },
  });
}

export function useConfigureInsurancePlan() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { planCode: string; terms: PlanTerms }>({
    mutationFn: ({ planCode, terms }) =>
      apiClient.put<ApiResponse<unknown>>(
        `/internal/v1/finance/insurance-plans/${encodeURIComponent(planCode)}`,
        terms,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: KEY });
    },
  });
}
