/**
 * Bank→wallet cash-in reconciliation via the Experience BFF.
 * POST /internal/v1/finance/statement-match  { lines: [{ narrative, amount, bankRef }] }
 * → per-line MATCHED / UNMATCHED outcomes.
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type StatementLine = { narrative: string; amount: number; bankRef: string };

export function useStatementMatch() {
  return useMutation<ApiResponse<unknown>, unknown, { lines: StatementLine[] }>({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/finance/statement-match", body),
  });
}
