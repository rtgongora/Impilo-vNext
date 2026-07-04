import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useSessionRealtime, type SessionRealtimeEvent } from "./useSessionRealtime";
import * as socket from "@/lib/realtime/khuluma-socket";

describe("useSessionRealtime", () => {
  let listeners: Array<(frame: Record<string, unknown>) => void>;

  beforeEach(() => {
    listeners = [];
    vi.spyOn(socket, "subscribeKhulumaRealtime").mockImplementation((listener) => {
      listeners.push(listener);
      return () => {
        listeners = listeners.filter((l) => l !== listener);
      };
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const emit = (frame: Record<string, unknown>) => listeners.forEach((l) => l(frame));

  it("delivers only session.* events for the subscribed session", () => {
    const events: SessionRealtimeEvent[] = [];
    renderHook(() =>
      useSessionRealtime({ sessionRef: "sess-1", onEvent: (e) => events.push(e) }),
    );

    emit({ event_type: "session.raise_hand", session_ref: "sess-1", actor_id: "a1" });
    emit({ event_type: "session.raise_hand", session_ref: "sess-2", actor_id: "a2" });
    emit({ event_type: "message.created", session_ref: "sess-1" });
    emit({ event_type: "session.admitted", sessionRef: "sess-1" });

    expect(events.map((e) => e.type)).toEqual(["session.raise_hand", "session.admitted"]);
    expect(events[0].actorId).toBe("a1");
  });

  it("does not subscribe when disabled or without a sessionRef", () => {
    renderHook(() => useSessionRealtime({ sessionRef: null, onEvent: () => {} }));
    renderHook(() => useSessionRealtime({ sessionRef: "s", enabled: false, onEvent: () => {} }));
    expect(listeners).toHaveLength(0);
  });

  it("unsubscribes on unmount", () => {
    const { unmount } = renderHook(() =>
      useSessionRealtime({ sessionRef: "sess-1", onEvent: () => {} }),
    );
    expect(listeners).toHaveLength(1);
    unmount();
    expect(listeners).toHaveLength(0);
  });
});
