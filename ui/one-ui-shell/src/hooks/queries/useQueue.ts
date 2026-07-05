/**
 * Experience UI — Queue Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface QueueEntryResource {
  id: string;
  type: "queue_entry";
  attributes: {
    patientId: string;
    facilityId: string;
    status: string;
    priority: number;
    queuedAt: string;
    [key: string]: unknown;
  };
}

interface QueueEntriesParams {
  facilityId?: string;
  status?: string;
  /** When set with facilityId, BFF resolves the queue for this PCT queue type (e.g. TRIAGE, LABORATORY). */
  queueType?: string;
}

type QueueEntriesResponse = ApiResponse<QueueEntryResource[]>;
type QueueEntryResponse = ApiResponse<QueueEntryResource>;

export function useQueueEntries(params?: QueueEntriesParams) {
  return useQuery<QueueEntriesResponse>({
    queryKey: ["queue-entries", params],
    enabled:
      !!params && (!!params.facilityId || (typeof params.status === "string" && params.status.length > 0)),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.facilityId) searchParams.set("facility_id", params.facilityId);
      if (params?.status) searchParams.set("status", params.status);
      if (params?.queueType) searchParams.set("queue_type", params.queueType);

      const qs = searchParams.toString();
      const path = `/internal/v1/queue/entries${qs ? `?${qs}` : ""}`;
      return apiClient.get<QueueEntriesResponse>(path);
    },
  });
}

export function useCallPatient() {
  const queryClient = useQueryClient();

  return useMutation<QueueEntryResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/call`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
    },
  });
}

export function useCompleteQueueEntry() {
  const queryClient = useQueryClient();

  return useMutation<QueueEntryResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/complete`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
    },
  });
}

export function useTransferQueueEntry() {
  const queryClient = useQueryClient();
  return useMutation<QueueEntryResponse, unknown, { id: string; targetQueueId: string }>({
    mutationFn: ({ id, targetQueueId }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/transfer`, {
        target_queue_id: targetQueueId,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
      queryClient.invalidateQueries({ queryKey: ["queue-stats"] });
    },
  });
}

export interface EscalateQueueEntryInput {
  id: string;
  /** Mandatory operational reason — BFF rejects blank reasons with 400 VALIDATION. */
  reason: string;
  /** Optional destination queue (e.g. emergency workspace). */
  targetQueueId?: string;
  /** Optional explicit priority override on the PCT 1–5 scale. */
  priority?: number;
}

export function useEscalateQueueEntry() {
  const queryClient = useQueryClient();
  return useMutation<QueueEntryResponse, unknown, EscalateQueueEntryInput>({
    mutationFn: ({ id, reason, targetQueueId, priority }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/escalate`, {
        reason,
        ...(targetQueueId ? { target_queue_id: targetQueueId } : {}),
        ...(priority != null ? { priority } : {}),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
      queryClient.invalidateQueries({ queryKey: ["queue-stats"] });
    },
  });
}

export function useAbandonQueueEntry() {
  const queryClient = useQueryClient();
  return useMutation<QueueEntryResponse, unknown, { id: string }>({
    mutationFn: ({ id }) => apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/abandon`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
      queryClient.invalidateQueries({ queryKey: ["queue-stats"] });
    },
  });
}
