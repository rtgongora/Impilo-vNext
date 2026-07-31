import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import * as transport from "@/lib/realtime/realtime-transport";
import { useEmergencyRealtime } from "./useEmergencyRealtime";

describe("useEmergencyRealtime", () => {
  beforeEach(() => {
    vi.spyOn(transport, "subscribeRealtime").mockImplementation((listener) => {
      (globalThis as { __emListener?: (f: transport.RealtimeFrame) => void }).__emListener = listener;
      return () => {
        delete (globalThis as { __emListener?: unknown }).__emListener;
      };
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("invalidates emergency board keys when an episode hint arrives", () => {
    const qc = new QueryClient();
    const invalidate = vi.spyOn(qc, "invalidateQueries");
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children);

    renderHook(() => useEmergencyRealtime(true), { wrapper });

    (globalThis as { __emListener?: (f: transport.RealtimeFrame) => void }).__emListener?.({
      event_type: "EMERGENCY_EPISODE_OPENED",
      channel: "tenant:00000000-0000-4000-8000-000000000001",
      episode_id: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
      revision: 1,
    });

    expect(invalidate).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ["emergency-command-board"] }),
    );
    expect(invalidate).toHaveBeenCalledWith(
      expect.objectContaining({
        queryKey: ["emergency-episode", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"],
      }),
    );
  });
});
