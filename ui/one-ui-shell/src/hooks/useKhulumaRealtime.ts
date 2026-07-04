"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { commsKeys } from "@/hooks/queries/useComms";
import { subscribeKhulumaRealtime, type RealtimeFrame } from "@/lib/realtime/khuluma-socket";

/**
 * Optional realtime client for the Khuluma gateway. Rides the SHARED
 * per-tab transport (src/lib/realtime/khuluma-socket.ts) so CommsHub and the
 * session shells never open competing sockets. When neither
 * `NEXT_PUBLIC_KHULUMA_WS` nor `NEXT_PUBLIC_KHULUMA_SSE` is set this is a
 * no-op and the Comms Hub still works via REST refetch — realtime is a pure
 * enhancement, never a hard dependency.
 */
export interface IncomingCall {
  callId: string;
  conversationId: string | null;
  initiatedBy: string;
  callType: string;
}

interface RealtimeOptions {
  enabled: boolean;
  onIncomingCall?: (call: IncomingCall) => void;
}

export function useKhulumaRealtime({ enabled, onIncomingCall }: RealtimeOptions) {
  const queryClient = useQueryClient();
  const callbackRef = useRef(onIncomingCall);
  callbackRef.current = onIncomingCall;

  useEffect(() => {
    if (!enabled || typeof window === "undefined") return;

    const handleFrame = (frame: RealtimeFrame) => {
      const eventType = String(frame.event_type ?? "");
      const conversationId = frame.conversation_id ? String(frame.conversation_id) : null;

      if (eventType === "message.created" && conversationId) {
        queryClient.invalidateQueries({ queryKey: commsKeys().messages(conversationId) });
        queryClient.invalidateQueries({ queryKey: commsKeys().inbox });
        queryClient.invalidateQueries({ queryKey: commsKeys().summary });
      } else if (eventType === "receipt.read") {
        queryClient.invalidateQueries({ queryKey: commsKeys().inbox });
      } else if (eventType === "presence.changed" && frame.actor_id) {
        queryClient.invalidateQueries({ queryKey: commsKeys().presence(String(frame.actor_id)) });
      } else if (eventType === "call.ringing" && frame.call_id) {
        callbackRef.current?.({
          callId: String(frame.call_id),
          conversationId,
          initiatedBy: String(frame.initiated_by ?? ""),
          callType: String(frame.call_type ?? "AUDIO"),
        });
      }
    };

    return subscribeKhulumaRealtime(handleFrame);
  }, [enabled, queryClient]);
}
