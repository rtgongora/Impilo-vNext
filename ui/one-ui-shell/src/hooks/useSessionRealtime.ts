"use client";

import { useEffect, useRef } from "react";
import { subscribeKhulumaRealtime, type RealtimeFrame } from "@/lib/realtime/khuluma-socket";

/**
 * Session-scoped realtime events over the shared Khuluma transport. The
 * dispatcher tags session-suite frames with `session_ref`; this hook filters
 * to one session and hands the caller typed events (raise-hand, lobby
 * admissions, stage promotions, poll launches…). Enhancement-only: shells
 * must keep their polling fallbacks.
 */
export type SessionRealtimeEventType =
  | "session.raise_hand"
  | "session.hand_lowered"
  | "session.lobby_joined"
  | "session.admitted"
  | "session.denied"
  | "session.promoted_to_stage"
  | "session.demoted"
  | "session.poll_launched"
  | "session.announcement"
  | "session.recording_changed";

export interface SessionRealtimeEvent {
  type: SessionRealtimeEventType | string;
  sessionRef: string;
  actorId?: string;
  payload: RealtimeFrame;
}

interface SessionRealtimeOptions {
  sessionRef: string | null | undefined;
  enabled?: boolean;
  onEvent: (event: SessionRealtimeEvent) => void;
}

export function useSessionRealtime({ sessionRef, enabled = true, onEvent }: SessionRealtimeOptions) {
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!enabled || !sessionRef) return;
    const unsubscribe = subscribeKhulumaRealtime((frame) => {
      const frameRef = String(frame.session_ref ?? frame.sessionRef ?? "");
      if (!frameRef || frameRef !== sessionRef) return;
      const type = String(frame.event_type ?? frame.type ?? "");
      if (!type.startsWith("session.")) return;
      handlerRef.current({
        type,
        sessionRef: frameRef,
        actorId: frame.actor_id ? String(frame.actor_id) : undefined,
        payload: frame,
      });
    });
    return unsubscribe;
  }, [enabled, sessionRef]);
}
