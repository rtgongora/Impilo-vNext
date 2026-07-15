/**
 * COSTA dead-lettered money events (ops dead-letter surface) via Experience BFF.
 *
 * - GET  /internal/v1/finance/failed-money-events?status=DEAD
 * - POST /internal/v1/finance/failed-money-events/{id}/replay
 *
 * Replay re-publishes the original payload onto the original topic; it is safe
 * because every money consumer is idempotent (costa_idempotency guards dupes).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type FailedMoneyEvent = {
  id: number;
  originalTopic: string;
  payload: string;
  errorMessage: string | null;
  status: string;
  createdAt: string;
  replayedAt: string | null;
  replayedBy: string | null;
};

const KEY = ["finance", "failed-money-events"] as const;

export function useFailedMoneyEvents(status?: string) {
  return useQuery<ApiResponse<FailedMoneyEvent[]>>({
    queryKey: [...KEY, status ?? "ALL"],
    queryFn: () => {
      const qs = status ? `?status=${encodeURIComponent(status)}` : "";
      return apiClient.get<ApiResponse<FailedMoneyEvent[]>>(
        `/internal/v1/finance/failed-money-events${qs}`,
      );
    },
  });
}

export function useReplayFailedMoneyEvent() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, number>({
    mutationFn: (id) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/finance/failed-money-events/${id}/replay`,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: KEY });
    },
  });
}
