import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  __resetRealtimeTransportForTests,
  subscribeRealtime,
} from "./realtime-transport";

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  onmessage: ((ev: { data: string }) => void) | null = null;
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(public url: string) {
    FakeWebSocket.instances.push(this);
  }

  close() {
    this.closed = true;
    this.onclose?.();
  }

  emit(frame: unknown) {
    this.onmessage?.({ data: JSON.stringify(frame) });
  }
}

describe("realtime-transport shared socket", () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeWebSocket as unknown as typeof WebSocket);
    vi.stubEnv("NEXT_PUBLIC_REALTIME_WS", "ws://gateway/realtime");
    vi.stubEnv("NEXT_PUBLIC_KHULUMA_WS", "");
    __resetRealtimeTransportForTests();
  });

  afterEach(() => {
    __resetRealtimeTransportForTests();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("shares ONE socket across subscribers and fans frames out", () => {
    const seenA: unknown[] = [];
    const seenB: unknown[] = [];
    const offA = subscribeRealtime((f) => seenA.push(f));
    const offB = subscribeRealtime((f) => seenB.push(f));

    expect(FakeWebSocket.instances).toHaveLength(1);
    FakeWebSocket.instances[0].emit({ event_type: "episode.state_changed", episode_id: "e1" });

    expect(seenA).toHaveLength(1);
    expect(seenB).toHaveLength(1);
    offA();
    offB();
  });

  it("closes the socket when the last subscriber leaves", () => {
    const off = subscribeRealtime(() => {});
    expect(FakeWebSocket.instances).toHaveLength(1);
    off();
    expect(FakeWebSocket.instances[0].closed).toBe(true);
  });

  it("falls back to NEXT_PUBLIC_KHULUMA_WS when REALTIME_WS is unset", () => {
    vi.stubEnv("NEXT_PUBLIC_REALTIME_WS", "");
    vi.stubEnv("NEXT_PUBLIC_KHULUMA_WS", "ws://khuluma/realtime");
    __resetRealtimeTransportForTests();
    const off = subscribeRealtime(() => {});
    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(FakeWebSocket.instances[0].url).toBe("ws://khuluma/realtime");
    off();
  });

  it("is a no-op without configured endpoints", () => {
    vi.stubEnv("NEXT_PUBLIC_REALTIME_WS", "");
    vi.stubEnv("NEXT_PUBLIC_KHULUMA_WS", "");
    __resetRealtimeTransportForTests();
    const off = subscribeRealtime(() => {});
    expect(FakeWebSocket.instances).toHaveLength(0);
    off();
  });
});
