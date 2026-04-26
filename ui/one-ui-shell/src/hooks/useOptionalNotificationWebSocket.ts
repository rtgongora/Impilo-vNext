"use client";

import { useEffect, useRef } from "react";
import { emitShellEvent } from "@/lib/shell/shell-events";
import { normalizeLiveNotificationPayload } from "@/lib/shell/live-notification";

/**
 * When `NEXT_PUBLIC_EXP_NOTIFICATIONS_WS` is set, opens an optional WebSocket and forwards
 * server messages into the shell event bus for the taskbar tray (no protocol contract enforced here).
 */
export function useOptionalNotificationWebSocket(enabled: boolean) {
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!enabled || typeof window === "undefined") return;
    const url = process.env.NEXT_PUBLIC_EXP_NOTIFICATIONS_WS?.trim();
    if (!url) return;

    let stopped = false;
    let attempt = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

    const connect = () => {
      if (stopped) return;
      try {
        const ws = new WebSocket(url);
        wsRef.current = ws;
        ws.onmessage = (ev) => {
          const raw = String(ev.data);
          let payload: Record<string, unknown> = { raw };
          try {
            const parsed = JSON.parse(raw) as unknown;
            if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
              payload = parsed as Record<string, unknown>;
            }
          } catch {
            /* non-JSON: treat whole string as body */
            payload = { message: raw.slice(0, 500) };
          }
          const n = normalizeLiveNotificationPayload(payload);
          emitShellEvent("live_notification", {
            title: n.title,
            message: n.body,
            severity: n.severity,
            _original: payload,
          });
        };
        ws.onopen = () => {
          attempt = 0;
        };
        ws.onclose = () => {
          wsRef.current = null;
          if (stopped) return;
          attempt += 1;
          const delay = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5));
          reconnectTimer = setTimeout(connect, delay);
        };
        ws.onerror = () => {
          ws.close();
        };
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
  }, [enabled]);
}
