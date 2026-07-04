"use client";

/**
 * Shared Khuluma realtime transport — ONE WebSocket per tab, fanned out to any
 * number of subscribers (CommsHub, session shells, meeting lobbies…). The
 * socket opens lazily with the first subscriber and closes with the last;
 * reconnects use exponential backoff (reset on a successful open). When
 * `NEXT_PUBLIC_KHULUMA_WS` is unset an `NEXT_PUBLIC_KHULUMA_SSE` EventSource
 * is tried; with neither, subscribe() is a no-op — realtime stays a pure
 * enhancement over REST polling, never a hard dependency.
 */

export type RealtimeFrame = Record<string, unknown>;
export type FrameListener = (frame: RealtimeFrame) => void;

interface Transport {
  close: () => void;
}

const listeners = new Set<FrameListener>();
let transport: Transport | null = null;
let stopped = false;
let attempt = 0;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

function dispatch(raw: string) {
  let frame: RealtimeFrame;
  try {
    frame = JSON.parse(raw) as RealtimeFrame;
  } catch {
    return;
  }
  for (const listener of [...listeners]) {
    try {
      listener(frame);
    } catch {
      // one bad listener must not break the fan-out
    }
  }
}

function scheduleReconnect(connect: () => void) {
  if (stopped || listeners.size === 0) return;
  attempt += 1;
  reconnectTimer = setTimeout(connect, Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5)));
}

function openTransport(): Transport | null {
  if (typeof window === "undefined") return null;

  const wsUrl = process.env.NEXT_PUBLIC_KHULUMA_WS?.trim();
  if (wsUrl) {
    try {
      const ws = new WebSocket(wsUrl);
      ws.onmessage = (ev) => dispatch(String(ev.data));
      ws.onopen = () => {
        attempt = 0;
      };
      ws.onclose = () => {
        transport = null;
        scheduleReconnect(ensureTransport);
      };
      ws.onerror = () => ws.close();
      return { close: () => ws.close() };
    } catch {
      scheduleReconnect(ensureTransport);
      return null;
    }
  }

  const sseUrl = process.env.NEXT_PUBLIC_KHULUMA_SSE?.trim();
  if (sseUrl && typeof EventSource !== "undefined") {
    try {
      const es = new EventSource(sseUrl);
      es.onmessage = (ev) => dispatch(String(ev.data));
      es.onopen = () => {
        attempt = 0;
      };
      es.onerror = () => {
        es.close();
        transport = null;
        scheduleReconnect(ensureTransport);
      };
      return { close: () => es.close() };
    } catch {
      scheduleReconnect(ensureTransport);
      return null;
    }
  }

  return null;
}

function ensureTransport() {
  if (stopped || transport || listeners.size === 0) return;
  transport = openTransport();
}

/** Subscribe to every realtime frame; returns the unsubscribe function. */
export function subscribeKhulumaRealtime(listener: FrameListener): () => void {
  listeners.add(listener);
  stopped = false;
  ensureTransport();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      stopped = true;
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      transport?.close();
      transport = null;
      attempt = 0;
    }
  };
}

/** Test hook: reset module state between tests. */
export function __resetKhulumaSocketForTests() {
  listeners.clear();
  stopped = true;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  transport?.close();
  transport = null;
  attempt = 0;
}
