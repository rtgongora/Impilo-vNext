"use client";

/**
 * Compatibility shim — the shared transport lives in realtime-transport.ts (W19).
 * Existing khuluma hooks keep importing this path.
 */
export {
  type RealtimeFrame,
  type FrameListener,
  subscribeRealtime,
  subscribeKhulumaRealtime,
  __resetRealtimeTransportForTests,
  __resetKhulumaSocketForTests,
} from "./realtime-transport";
