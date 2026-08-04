"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface AssistantTrayNotification {
  id: string;
  type: string;
  severity: string;
  title: string;
  body: string;
  action?: { label: string; href: string };
  dismissible: boolean;
  timestamp: string;
  source: string;
}

/**
 * Notification scope is server-derived (Target Architecture v1.3.2 §40.1,
 * backlog item 83): the BFF resolves work mode, facility and shift from the
 * authenticated session's own work-context resolution. The client no longer
 * sends them — a browser asserting its own notification scope was the same
 * defect class the trust plane eliminates for authority headers, and the
 * server now ignores such parameters by construction.
 */
export function assistantNotificationsQueryKey() {
  return ["assistant-notifications"] as const;
}

export function useAssistantNotifications(enabled = true) {
  return useQuery({
    queryKey: assistantNotificationsQueryKey(),
    enabled,
    staleTime: 20_000,
    refetchInterval: 45_000,
    queryFn: async () => {
      const res = await apiClient.get<{ data: AssistantTrayNotification[] }>(
        "/internal/v1/assistant/notifications",
      );
      return res?.data ?? [];
    },
  });
}
