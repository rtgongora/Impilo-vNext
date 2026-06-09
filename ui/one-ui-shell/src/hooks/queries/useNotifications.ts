/**
 * Experience UI — Notifications Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

function inboxRecipientId(user: ReturnType<typeof useAuthStore.getState>["user"]) {
  if (!user) return undefined;
  if (user.actorType === "CITIZEN") {
    return user.healthId ?? user.id;
  }
  if (user.providerActivated && user.providerId) {
    return `actor:${user.providerId}`;
  }
  if (user.staffId) {
    return `staff:${user.staffId}`;
  }
  return user.id;
}

export function useNotifications() {
  const user = useAuthStore((state) => state.user);
  const recipientId = inboxRecipientId(user);
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["notifications", recipientId ?? "all"],
    queryFn: () => {
      const qs = recipientId ? `?recipientId=${encodeURIComponent(recipientId)}` : "";
      return apiClient.get(`/internal/v1/notifications${qs}`);
    },
    enabled: !!user,
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
