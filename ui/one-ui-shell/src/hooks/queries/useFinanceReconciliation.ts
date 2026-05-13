/**
 * MusheX reconciliation via Experience BFF.
 *
 * - POST /internal/v1/finance/reconciliation/import-statement
 * - GET  /internal/v1/finance/reconciliation/unmatched
 * - POST /internal/v1/finance/reconciliation/match?reconId=...
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FinanceReconJson = unknown;

export function useReconciliationUnmatched(params: { page?: number; size?: number }, enabled: boolean) {
  const sp = new URLSearchParams();
  if (params.page != null) sp.set("page", String(params.page));
  if (params.size != null) sp.set("size", String(params.size));
  const q = sp.toString();
  return useQuery<FinanceReconJson>({
    queryKey: ["finance", "reconciliation", "unmatched", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<FinanceReconJson>(
        `/internal/v1/finance/reconciliation/unmatched${q ? `?${q}` : ""}`,
      ),
    enabled,
  });
}

export function useReconciliationImportStatement() {
  const qc = useQueryClient();
  return useMutation<FinanceReconJson, unknown, unknown>({
    mutationFn: (body: unknown) =>
      apiClient.post<FinanceReconJson>("/internal/v1/finance/reconciliation/import-statement", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "reconciliation"] });
    },
  });
}

export function useReconciliationMatch() {
  const qc = useQueryClient();
  return useMutation<FinanceReconJson, unknown, { reconId: string; body: unknown }>({
    mutationFn: ({ reconId, body }) =>
      apiClient.post<FinanceReconJson>(
        `/internal/v1/finance/reconciliation/match?reconId=${encodeURIComponent(reconId)}`,
        body,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "reconciliation"] });
    },
  });
}

export function useReconciliationTripleMatch(encounterId: string, enabled: boolean) {
  const q = new URLSearchParams();
  if (encounterId) q.set("encounterId", encounterId);
  const suffix = q.toString();
  return useQuery<FinanceReconJson>({
    queryKey: ["finance", "reconciliation", "triple-match", encounterId],
    queryFn: () =>
      apiClient.get<FinanceReconJson>(
        `/internal/v1/finance/reconciliation/triple-match${suffix ? `?${suffix}` : ""}`,
      ),
    enabled: enabled && encounterId.trim().length > 0,
  });
}
