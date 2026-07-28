/**
 * Phase G3 (increment 2) — entering an app mode must be backed by a granted
 * duty token, not a local enum flip. These tests hold the line that made
 * "supervisor grants no automatic patient access" structural.
 */
import { describe, expect, it, vi, beforeEach } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";

const switchContextMocks = vi.hoisted(() => ({
  switchWorkContext: vi.fn(),
  switching: false,
  error: null as string | null,
}));
vi.mock("./useSwitchWorkContext", () => ({ useSwitchWorkContext: () => switchContextMocks }));

const authMocks = vi.hoisted(() => ({ session: { workContextId: "ctx-1" } as Record<string, unknown> | null }));
vi.mock("@impilo/mobile-auth", () => ({ useAuth: () => authMocks }));

const storeState = vi.hoisted(() => ({
  resolvedWorkContexts: null as ResolvedWorkContextView[] | null,
  setMode: vi.fn(),
}));
vi.mock("../stores/appStore", () => ({
  useAppStore: () => storeState,
  appStore: { getState: () => storeState, subscribe: vi.fn(() => () => {}) },
}));

import { useSwitchAppMode } from "./useSwitchAppMode";

type HookValue = ReturnType<typeof useSwitchAppMode>;

function renderHook(): { current: () => HookValue } {
  let latest: HookValue;
  function Probe() {
    latest = useSwitchAppMode();
    return null;
  }
  const root = createRoot(document.createElement("div"));
  act(() => root.render(React.createElement(Probe)));
  return { current: () => latest };
}

function ctx(overrides: Partial<ResolvedWorkContextView>): ResolvedWorkContextView {
  return {
    contextId: "ctx-1",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: [],
    restrictions: [],
    label: "Harare Central",
    groupHint: "today",
    ...overrides,
  };
}

describe("useSwitchAppMode (G3)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    switchContextMocks.error = null;
    switchContextMocks.switching = false;
    authMocks.session = { workContextId: "ctx-1" };
    storeState.resolvedWorkContexts = null;
  });

  it("mints a management-mode token before entering supervisor", async () => {
    storeState.resolvedWorkContexts = [ctx({ availableModes: ["CLINICAL_CARE", "FACILITY_MANAGEMENT"] })];
    switchContextMocks.switchWorkContext.mockResolvedValue(true);
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchAppMode("supervisor");
    });

    expect(ok).toBe(true);
    expect(switchContextMocks.switchWorkContext).toHaveBeenCalledWith(
      expect.objectContaining({ contextId: "ctx-1" }),
      "FACILITY_MANAGEMENT"
    );
    expect(storeState.setMode).toHaveBeenCalledWith("supervisor");
  });

  it("does NOT move the visible mode when the mint fails", async () => {
    storeState.resolvedWorkContexts = [ctx({ availableModes: ["FACILITY_MANAGEMENT"] })];
    switchContextMocks.switchWorkContext.mockResolvedValue(false);
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchAppMode("supervisor");
    });

    expect(ok).toBe(false);
    expect(storeState.setMode).not.toHaveBeenCalled();
  });

  it("refuses a mode no resolved assignment grants, rather than entering it locally", async () => {
    storeState.resolvedWorkContexts = [ctx({ availableModes: ["CLINICAL_CARE"] })];
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchAppMode("supervisor");
    });

    expect(ok).toBe(false);
    expect(switchContextMocks.switchWorkContext).not.toHaveBeenCalled();
    expect(storeState.setMode).not.toHaveBeenCalled();
    expect(result.current().error).toContain("grant this way of working");
  });

  it("mints a clinical-mode token before entering provider", async () => {
    storeState.resolvedWorkContexts = [ctx({ availableModes: ["CLINICAL_CARE"] })];
    switchContextMocks.switchWorkContext.mockResolvedValue(true);
    const result = renderHook();

    await act(async () => {
      await result.current().switchAppMode("provider");
    });

    expect(switchContextMocks.switchWorkContext).toHaveBeenCalledWith(expect.anything(), "CLINICAL_CARE");
  });

  it("lets modes with no WorkMode analogue switch locally without minting", async () => {
    const result = renderHook();

    for (const mode of ["outreach", "courier", "offline"] as const) {
      storeState.setMode.mockClear();
      switchContextMocks.switchWorkContext.mockClear();
      let ok: boolean | undefined;
      await act(async () => {
        ok = await result.current().switchAppMode(mode);
      });
      expect(ok, `${mode} should switch locally`).toBe(true);
      expect(storeState.setMode).toHaveBeenCalledWith(mode);
      expect(switchContextMocks.switchWorkContext).not.toHaveBeenCalled();
    }
  });

  it("prefers staying in the context the person is already working in", async () => {
    storeState.resolvedWorkContexts = [
      ctx({ contextId: "ctx-other", availableModes: ["FACILITY_MANAGEMENT"], label: "Other" }),
      ctx({ contextId: "ctx-1", availableModes: ["FACILITY_MANAGEMENT"] }),
    ];
    switchContextMocks.switchWorkContext.mockResolvedValue(true);
    const result = renderHook();

    await act(async () => {
      await result.current().switchAppMode("supervisor");
    });

    expect(switchContextMocks.switchWorkContext).toHaveBeenCalledWith(
      expect.objectContaining({ contextId: "ctx-1" }),
      "FACILITY_MANAGEMENT"
    );
  });
});
