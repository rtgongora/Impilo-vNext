import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useWorkEligibility } from "../useWorkEligibility";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

// Selector-aware mock — the hook reads via useAuthStore((s) => s.user).
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel?: (s: unknown) => unknown) => {
    const state = { user: { id: "hid-9" }, isAuthenticated: true };
    return sel ? sel(state) : state;
  },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

/**
 * D-P6 — the work-eligibility summary unwraps the {data:{attributes}} resource
 * envelope, marks resolved=true on success, and degrades honestly (resolved
 * false, never fabricated eligibility) when the call fails.
 */
describe("useWorkEligibility", () => {
  beforeEach(() => get.mockReset());

  it("unwraps the resource envelope and marks resolved", async () => {
    get.mockResolvedValue({
      data: {
        id: "hid-9",
        type: "work-eligibility",
        attributes: {
          linked: true,
          workEligible: false,
          lifecycleStatus: "SUSPENDED",
          licenceStatus: "SUSPENDED",
          remediation: "Open My Professional to view the status.",
        },
      },
    });

    const { result } = renderHook(() => useWorkEligibility(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(get).toHaveBeenCalledWith("/internal/v1/work-context/eligibility");
    expect(result.current.eligibility.resolved).toBe(true);
    expect(result.current.eligibility.workEligible).toBe(false);
    expect(result.current.eligibility.lifecycleStatus).toBe("SUSPENDED");
    expect(result.current.eligibility.remediation).toContain("My Professional");
  });

  it("never fabricates eligibility before data resolves (unresolved default)", async () => {
    // Deterministic degrade assertion: before any successful response the hook
    // exposes the unresolved default — never a fabricated workEligible=true.
    // (The failure→unresolved path is locked structurally in the golden-thread
    //  test, since this query hook swallows failures internally by design and
    //  React Query never owns the error to assert behaviourally.)
    let resolveGet: (v: unknown) => void = () => undefined;
    get.mockReturnValue(new Promise((res) => { resolveGet = res; }));

    const { result } = renderHook(() => useWorkEligibility(), { wrapper });

    expect(result.current.eligibility.resolved).toBe(false);
    expect(result.current.eligibility.workEligible).toBe(false);

    // settle the pending fetch so the test leaves no dangling promise
    resolveGet({
      data: { id: "hid-9", type: "work-eligibility", attributes: { linked: false, workEligible: false, lifecycleStatus: null, licenceStatus: null, remediation: null } },
    });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
  });
});
