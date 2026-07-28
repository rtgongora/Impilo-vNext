/**
 * Phase G4 — the switch's safety ordering. Mobile has no page reload to
 * guarantee a clean slate, so these orderings ARE the guarantee: a refused
 * switch must leave the current session untouched, and a failed mint must
 * never leave the previous context's duty token in place.
 */
import { describe, expect, it, vi, beforeEach } from "vitest";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";

const partitionMocks = vi.hoisted(() => ({
  canPartitionSafely: vi.fn(),
  clearContextScopedCaches: vi.fn(),
}));
vi.mock("./contextCachePartition", () => partitionMocks);

const contextMocks = vi.hoisted(() => ({ mintWorkContextSession: vi.fn() }));
vi.mock("../services/workContextService", () => contextMocks);

const authMocks = vi.hoisted(() => ({
  setWorkContext: vi.fn(),
  clearWorkContext: vi.fn(),
  setFacility: vi.fn(),
  session: { workContextJti: "old-jti" } as Record<string, unknown> | null,
}));
vi.mock("@impilo/mobile-auth", () => ({ useAuth: () => authMocks }));

const storeMocks = vi.hoisted(() => ({
  clearContext: vi.fn(),
  setFacilityContext: vi.fn(),
}));
vi.mock("../stores/appStore", () => ({
  appStore: { getState: () => storeMocks, subscribe: vi.fn(() => () => {}) },
}));

import { useSwitchWorkContext } from "./useSwitchWorkContext";

type HookValue = ReturnType<typeof useSwitchWorkContext>;

/**
 * Minimal hook harness — @testing-library/react is not a dependency of this app
 * (only its react-native sibling, which no test here uses), so the hook is
 * driven through the same createRoot pattern the screen tests use.
 */
function renderHook(): { current: () => HookValue } {
  let latest: HookValue;
  function Probe() {
    latest = useSwitchWorkContext();
    return null;
  }
  const root = createRoot(document.createElement("div"));
  act(() => root.render(React.createElement(Probe)));
  return { current: () => latest };
}

const CONTEXT: ResolvedWorkContextView = {
  contextId: "ctx-2",
  contextKind: "facility",
  sourceSystem: "VASHANDI",
  facilityId: "fac-2",
  availableModes: ["CLINICAL_CARE"],
  defaultMode: "CLINICAL_CARE",
  restrictions: [],
  label: "Parirenyatwa",
  groupHint: "regular",
};

describe("useSwitchWorkContext (G4)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMocks.session = { workContextJti: "old-jti" };
    partitionMocks.canPartitionSafely.mockResolvedValue({ safe: true });
    partitionMocks.clearContextScopedCaches.mockResolvedValue(undefined);
  });

  it("refuses the switch and leaves the session untouched when work is unsynced", async () => {
    partitionMocks.canPartitionSafely.mockResolvedValue({
      safe: false,
      reason: "You have 2 unsynced changes. Sync before switching workplace.",
    });
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchWorkContext(CONTEXT);
    });

    expect(ok).toBe(false);
    expect(result.current().error).toContain("2 unsynced changes");
    // Nothing torn down — the person must still be able to sync.
    expect(authMocks.clearWorkContext).not.toHaveBeenCalled();
    expect(contextMocks.mintWorkContextSession).not.toHaveBeenCalled();
    expect(partitionMocks.clearContextScopedCaches).not.toHaveBeenCalled();
  });

  it("clears the old duty token BEFORE minting, so a failed mint cannot leave it in place", async () => {
    contextMocks.mintWorkContextSession.mockRejectedValue(new Error("network down"));
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchWorkContext(CONTEXT);
    });

    expect(ok).toBe(false);
    expect(authMocks.clearWorkContext).toHaveBeenCalled();
    expect(authMocks.setWorkContext).not.toHaveBeenCalled();
    expect(result.current().error).toContain("Could not switch workplace");
  });

  it("passes the previous jti so the BFF revokes the old session", async () => {
    contextMocks.mintWorkContextSession.mockResolvedValue({
      token: "t2",
      jti: "jti-2",
      contextId: "ctx-2",
      workMode: "CLINICAL_CARE",
    });
    const result = renderHook();

    await act(async () => {
      await result.current().switchWorkContext(CONTEXT);
    });

    expect(contextMocks.mintWorkContextSession).toHaveBeenCalledWith("ctx-2", "CLINICAL_CARE", "old-jti");
  });

  it("partitions the offline cache and repoints facility state on a successful switch", async () => {
    contextMocks.mintWorkContextSession.mockResolvedValue({
      token: "t2",
      jti: "jti-2",
      contextId: "ctx-2",
      workMode: "CLINICAL_CARE",
    });
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchWorkContext(CONTEXT);
    });

    expect(ok).toBe(true);
    expect(partitionMocks.clearContextScopedCaches).toHaveBeenCalled();
    expect(storeMocks.clearContext).toHaveBeenCalled();
    expect(storeMocks.setFacilityContext).toHaveBeenCalledWith("fac-2", "Parirenyatwa");
    expect(authMocks.setWorkContext).toHaveBeenCalledWith(
      expect.objectContaining({ token: "t2", jti: "jti-2", contextId: "ctx-2", workMode: "CLINICAL_CARE" })
    );
  });

  it("refuses a context that offers no work mode at all", async () => {
    const result = renderHook();

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current().switchWorkContext({ ...CONTEXT, defaultMode: null, availableModes: [] });
    });

    expect(ok).toBe(false);
    expect(result.current().error).toContain("no available work mode");
    expect(contextMocks.mintWorkContextSession).not.toHaveBeenCalled();
  });
});
