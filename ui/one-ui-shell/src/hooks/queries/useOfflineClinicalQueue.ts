import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export function useOfflineClinicalQueue(facilityId?: string) {
  return useQuery({
    queryKey: ["offline-clinical-queue", facilityId],
    queryFn: async () => {
      const response = await apiClient.get<{ data: Record<string, unknown> }>(
        `/internal/v1/clinical-tools/offline/queue?facility_id=${encodeURIComponent(facilityId ?? "")}`,
      );
      return response.data ?? {};
    },
    enabled: !!facilityId,
  });
}

export function usePendingOfflineReconcile() {
  return useQuery({
    queryKey: ["offline-reconcile-pending"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: unknown[] }>(
        "/internal/v1/clinical-tools/offline/reconcile/pending",
      );
      return Array.isArray(response.data) ? response.data : [];
    },
  });
}

export function useSubmitOfflineReconcile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<{ data: Record<string, unknown> }>(
        "/internal/v1/clinical-tools/offline/reconcile",
        body,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["offline-reconcile-pending"] });
      void qc.invalidateQueries({ queryKey: ["offline-clinical-queue"] });
    },
  });
}
