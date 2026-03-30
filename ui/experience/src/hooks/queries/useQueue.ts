/**
 * Experience UI — Queue Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface QueueEntryResource {
  id: string;
  type: "queue_entry";
  attributes: {
    patient_id: string;
    facility_id: string;
    workspace_id: string;
    queue_type: string;
    priority: string;
    status: string;
    arrival_time: string;
    reason: string | null;
    triage_category: string | null;
    assigned_to: string | null;
    called_at: string | null;
    completed_at: string | null;
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
