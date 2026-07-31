"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { subscribeRealtime, type RealtimeFrame } from "@/lib/realtime/realtime-transport";

/**
 * Invalidates emergency episode/board queries when an invalidation hint arrives.
 * Frames are `{ event_type, channel, episode_id, revision }` — never clinical payload.
 *
 * No-op when neither NEXT_PUBLIC_REALTIME_* nor NEXT_PUBLIC_KHULUMA_* is set. Requires the
 * Khuluma↔PCT Redis bridge (pct:emergency:realtime) so browser sockets actually receive hints.
 */
export function useEmergencyRealtime(enabled = true) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled || typeof window === "undefined") return;

    const handleFrame = (frame: RealtimeFrame) => {
      const episodeId = frame.episode_id ? String(frame.episode_id) : null;
      const channel = frame.channel ? String(frame.channel) : "";
      const looksEmergency =
        channel.startsWith("episode:") ||
        (typeof frame.event_type === "string" &&
          String(frame.event_type).toUpperCase().includes("EMERGENCY"));

      if (!episodeId && !looksEmergency) {
        return;
      }

      void queryClient.invalidateQueries({ queryKey: ["emergency-episode-board"] });
      void queryClient.invalidateQueries({ queryKey: ["emergency-command-board"] });
      void queryClient.invalidateQueries({ queryKey: ["emergency-command-summary"] });
      void queryClient.invalidateQueries({ queryKey: ["emergency-alert-board"] });

      if (episodeId) {
        void queryClient.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
        void queryClient.invalidateQueries({ queryKey: ["emergency-episode-handovers", episodeId] });
        void queryClient.invalidateQueries({ queryKey: ["emergency-episode-alerts", episodeId] });
        void queryClient.invalidateQueries({ queryKey: ["emergency-observation-stays", episodeId] });
        void queryClient.invalidateQueries({ queryKey: ["emergency-disposition", episodeId] });
      }
    };

    return subscribeRealtime(handleFrame);
  }, [enabled, queryClient]);
}
