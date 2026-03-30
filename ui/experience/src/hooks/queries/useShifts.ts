/**
 * Experience UI — Shifts Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ShiftResource {
  id: string;
  type: "shift";
  attributes: {
    userId: string;
    facilityId: string;
    workspaceId: string;
    startedAt: string;
    endedAt: string | null;
    status: string;
    [key: string]: unknown;
  };
}

interface StartShiftPayload {
  facility_id: string;
  workspace_id: string;
  user_id: string;
}

type ShiftResponse = ApiResponse<ShiftResource>;

export function useCurrentShift(userId: string) {
  return useQuery<ShiftResponse>({
    queryKey: ["shifts", "current", userId],
    queryFn: () =>
      apiClient.get<ShiftResponse>(`/internal/v1/shifts/current?user_id=${encodeURIComponent(userId)}`),
    enabled: !!userId,
  });
}

export function useStartShift() {
  const queryClient = useQueryClient();

  return useMutation<ShiftResponse, unknown, StartShiftPayload>({
    mutationFn: (payload: StartShiftPayload) =>
      apiClient.post<ShiftResponse>("/internal/v1/shifts/start", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shifts"] });
    },
  });
}

export function useEndShift() {
  const queryClient = useQueryClient();

  return useMutation<ShiftResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<ShiftResponse>(`/internal/v1/shifts/${id}/end`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shifts"] });
    },
  });
}
