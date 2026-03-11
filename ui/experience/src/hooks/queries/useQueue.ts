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
}

type QueueEntriesResponse = ApiResponse<QueueEntryResource[]>;
type QueueEntryResponse = ApiResponse<QueueEntryResource>;

export function useQueueEntries(params?: QueueEntriesParams) {
  return useQuery<QueueEntriesResponse>({
    queryKey: ["queue-entries", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.facilityId) searchParams.set("facility_id", params.facilityId);
      if (params?.status) searchParams.set("status", params.status);

      const qs = searchParams.toString();
      const path = `/internal/v1/queue/entries${qs ? `?${qs}` : ""}`;
      return apiClient.get<QueueEntriesResponse>(path);
    },
  });
}

export function useCallPatient() {
  const queryClient = useQueryClient();

  return useMutation<QueueEntryResponse, unknown, { id: string }>({
    mutationFn: ({ id }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/call`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
    },
  });
}

export function useCompleteQueueEntry() {
  const queryClient = useQueryClient();

  return useMutation<QueueEntryResponse, unknown, { id: string }>({
    mutationFn: ({ id }) =>
      apiClient.post<QueueEntryResponse>(`/internal/v1/queue/entries/${id}/complete`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
    },
  });
}
