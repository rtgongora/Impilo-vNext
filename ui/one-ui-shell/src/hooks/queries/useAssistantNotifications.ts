"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";

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

export function assistantNotificationsQueryKey(
  workMode: string | undefined,
  facilityId: string | undefined,
  shiftId: string | undefined,
) {
  return ["assistant-notifications", workMode ?? "", facilityId ?? "", shiftId ?? ""] as const;
}

export function useAssistantNotifications(enabled = true) {
  const mode = useWorkModeStore((s) => s.mode);
  const facilityId = useFacilityStore((s) => s.facility?.id);
  const shiftId = useShiftStore((s) => s.shift?.id);

  return useQuery({
    queryKey: assistantNotificationsQueryKey(mode, facilityId, shiftId),
    enabled,
    staleTime: 20_000,
    refetchInterval: 45_000,
    queryFn: async () => {
      const params = new URLSearchParams();
      if (mode) params.set("work_mode", mode);
      if (facilityId) params.set("facility_id", facilityId);
      if (shiftId) params.set("shift_id", shiftId);
      const res = await apiClient.get<{ data: AssistantTrayNotification[] }>(
        `/internal/v1/assistant/notifications?${params.toString()}`,
      );
      return res?.data ?? [];
    },
  });
}
