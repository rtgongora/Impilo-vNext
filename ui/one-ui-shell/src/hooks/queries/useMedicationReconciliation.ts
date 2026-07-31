/**
 * Medication reconciliation — BFF /internal/v1/medication-reconciliations → PCT V107.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface MedRecEvent {
  reconciliation_id: string;
  status: string;
  context: string;
  started_at: string | null;
  completed_at: string | null;
}

export interface MedRecItem {
  item_id: string;
  display: string;
  finding: string | null;
  patient_says: string | null;
  system_says: string | null;
  action: string | null;
}

export function useMedicationReconciliations(subjectCpid: string) {
  return useQuery({
    queryKey: ["medication-reconciliations", subjectCpid],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/medication-reconciliations?subject_cpid=${encodeURIComponent(subjectCpid)}`
      );
      const rows = res.data ?? [];
      return {
        ...res,
        data: rows.map(
          (row): MedRecEvent => ({
            reconciliation_id: String(row.reconciliation_id ?? ""),
            status: String(row.status ?? ""),
            context: String(row.context ?? ""),
            started_at: row.started_at == null ? null : String(row.started_at),
            completed_at: row.completed_at == null ? null : String(row.completed_at),
          })
        ),
      } as ApiResponse<MedRecEvent[]>;
    },
    enabled: !!subjectCpid,
  });
}

export function useMedicationReconciliationItems(reconciliationId: string | null) {
  return useQuery({
    queryKey: ["medication-reconciliation-items", reconciliationId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/medication-reconciliations/${reconciliationId}/items`
      );
      const rows = res.data ?? [];
      return {
        ...res,
        data: rows.map(
          (row): MedRecItem => ({
            item_id: String(row.item_id ?? ""),
            display: String(row.display ?? ""),
            finding: row.finding == null ? null : String(row.finding),
            patient_says: row.patient_says == null ? null : String(row.patient_says),
            system_says: row.system_says == null ? null : String(row.system_says),
            action: row.action == null ? null : String(row.action),
          })
        ),
      } as ApiResponse<MedRecItem[]>;
    },
    enabled: !!reconciliationId,
  });
}
