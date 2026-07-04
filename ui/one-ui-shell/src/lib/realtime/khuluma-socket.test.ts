import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  __resetKhulumaSocketForTests,
  subscribeKhulumaRealtime,
} from "./khuluma-socket";

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

describe("khuluma-socket shared transport", () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeWebSocket as unknown as typeof WebSocket);
    vi.stubEnv("NEXT_PUBLIC_KHULUMA_WS", "ws://gateway/realtime");
    __resetKhulumaSocketForTests();
  });

  afterEach(() => {
    __resetKhulumaSocketForTests();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("shares ONE socket across subscribers and fans frames out", () => {
    const seenA: unknown[] = [];
    const seenB: unknown[] = [];
    const offA = subscribeKhulumaRealtime((f) => seenA.push(f));
    const offB = subscribeKhulumaRealtime((f) => seenB.push(f));

    expect(FakeWebSocket.instances).toHaveLength(1);
    FakeWebSocket.instances[0].emit({ event_type: "message.created", conversation_id: "c1" });

    expect(seenA).toHaveLength(1);
    expect(seenB).toHaveLength(1);
    offA();
    offB();
  });

  it("closes the socket when the last subscriber leaves", () => {
    const off = subscribeKhulumaRealtime(() => {});
    expect(FakeWebSocket.instances).toHaveLength(1);
    off();
    expect(FakeWebSocket.instances[0].closed).toBe(true);
  });

  it("a throwing listener does not break the fan-out", () => {
    const seen: unknown[] = [];
    const offBad = subscribeKhulumaRealtime(() => {
      throw new Error("bad listener");
    });
    const offGood = subscribeKhulumaRealtime((f) => seen.push(f));
    FakeWebSocket.instances[0].emit({ event_type: "presence.changed", actor_id: "a1" });
    expect(seen).toHaveLength(1);
    offBad();
    offGood();
  });

  it("is a no-op without configured endpoints", () => {
    vi.stubEnv("NEXT_PUBLIC_KHULUMA_WS", "");
    __resetKhulumaSocketForTests();
    const off = subscribeKhulumaRealtime(() => {});
    expect(FakeWebSocket.instances).toHaveLength(0);
    off();
  });
});
