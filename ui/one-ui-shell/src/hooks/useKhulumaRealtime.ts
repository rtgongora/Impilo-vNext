"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { commsKeys } from "@/hooks/queries/useComms";

/**
 * Optional realtime client for the Khuluma gateway (the SSE/WS endpoint built in W1.3). When
 * `NEXT_PUBLIC_KHULUMA_WS` is set (a BFF/Envoy-proxied path that injects the trust headers the
 * gateway's companion filter requires — browsers cannot set them directly), this opens a WebSocket
 * and turns inbound frames into TanStack-Query invalidations + incoming-call callbacks. When the
 * env var is absent it is a no-op and the Comms Hub still works via REST refetch — realtime is a
 * pure enhancement, never a hard dependency.
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
  const wsRef = useRef<WebSocket | null>(null);
  const callbackRef = useRef(onIncomingCall);
  callbackRef.current = onIncomingCall;

  useEffect(() => {
    if (!enabled || typeof window === "undefined") return;
    const url = process.env.NEXT_PUBLIC_KHULUMA_WS?.trim();
    if (!url) return;

    let stopped = false;
    let attempt = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

    const handleFrame = (raw: string) => {
      let frame: Record<string, unknown>;
      try {
        frame = JSON.parse(raw) as Record<string, unknown>;
      } catch {
        return;
      }
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

    const connect = () => {
      if (stopped) return;
      try {
        const ws = new WebSocket(url);
        wsRef.current = ws;
        ws.onmessage = (ev) => handleFrame(String(ev.data));
        ws.onopen = () => {
          attempt = 0;
        };
        ws.onclose = () => {
          wsRef.current = null;
          if (stopped) return;
          attempt += 1;
          reconnectTimer = setTimeout(connect, Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5)));
        };
        ws.onerror = () => ws.close();
      } catch {
        attempt += 1;
        reconnectTimer = setTimeout(connect, Math.min(30_000, 2000 * attempt));
      }
    };

    connect();
    return () => {
      stopped = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [enabled, queryClient]);
}
