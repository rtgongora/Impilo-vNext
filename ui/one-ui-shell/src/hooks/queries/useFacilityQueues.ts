import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Admin queue materialisation-visibility hooks. Backed by policy-protected experience-bff routes under
 * /internal/v1/admin/** which proxy PCT. Queue definitions are owned by TUSO facility service-point/
 * workspace configuration and materialised into PCT for care operations — these hooks read/reconcile
 * that materialised state; the BFF reports exactly what PCT says (no fabricated status).
 */

const BASE = "/internal/v1/admin/facilities";

export interface QueueMaterializationStatus {
  facilityId: string;
  overall: string;
  totalQueues: number;
  materialisedFromTuso: number;
  seedDemoOnly: number;
  lastMaterializedAt: string | null;
  byStatus: Record<string, number>;
  note?: string;
}

export interface FacilityQueueRow {
  queueId: string;
  name: string;
  queueType?: string;
  active: boolean;
  source: string;
  sourceRef?: string | null;
  materializationStatus: string;
  lastMaterializedAt: string | null;
  [key: string]: unknown;
}

export interface ReconcileResult {
  facilityId: string;
  status: string;
  created: number;
  updated: number;
  retired: number;
  skipped: number;
  tusoDefinitions: number;
  message: string | null;
}

export function useQueueMaterializationStatus(facilityId: string | undefined) {
  return useQuery<ApiResponse<QueueMaterializationStatus>>({
    queryKey: ["queue-materialization-status", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<QueueMaterializationStatus>>(
        `${BASE}/${facilityId}/queues/materialization-status`,
      ),
    enabled: !!facilityId,
  });
}

export function useFacilityQueuesAdmin(facilityId: string | undefined) {
  return useQuery<ApiResponse<FacilityQueueRow[]>>({
    queryKey: ["facility-queues-admin", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityQueueRow[]>>(`${BASE}/${facilityId}/queues`),
    enabled: !!facilityId,
  });
}

export function useReconcileQueues(facilityId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<ReconcileResult>>(`${BASE}/${facilityId}/queues/reconcile`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["queue-materialization-status", facilityId] });
      qc.invalidateQueries({ queryKey: ["facility-queues-admin", facilityId] });
    },
  });
}
