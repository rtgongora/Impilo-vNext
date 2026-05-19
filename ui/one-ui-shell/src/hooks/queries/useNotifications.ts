/**
 * Experience UI — Notifications Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useNotifications() {
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["notifications"],
    queryFn: () => apiClient.get("/internal/v1/notifications"),
  });
}

export function useMarkNotificationRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.patch(`/internal/v1/notifications/${id}/read`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}
