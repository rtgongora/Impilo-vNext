import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useSessionExperienceContract } from "./useSessionExperienceContract";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { user: { id: string; providerId?: string }; isAuthenticated: boolean }) => unknown) =>
    selector({ user: { id: "hid-1", providerId: "prov-1" }, isAuthenticated: true }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { hasFacility: boolean }) => unknown) => selector({ hasFacility: false }),
}));

const { useWorkAssignmentsMock } = vi.hoisted(() => ({ useWorkAssignmentsMock: vi.fn() }));

vi.mock("@/hooks/queries/useWorkAssignments", () => ({
  useWorkAssignments: () => useWorkAssignmentsMock(),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useSessionExperienceContract (Phase F10 — decoupled from useWorkAssignments)", () => {
  beforeEach(() => {
    get.mockReset();
    useWorkAssignmentsMock.mockReturnValue({ data: [] });
  });

  it("fires the BFF fetch even while useWorkAssignments has not resolved yet (empty array)", async () => {
    get.mockResolvedValue({ data: { id: "hid-1", type: "session-experience", attributes: { authenticated: true } } });

    const { result } = renderHook(() => useSessionExperienceContract(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(get).toHaveBeenCalledTimes(1);
    expect(get).toHaveBeenCalledWith("/internal/v1/session/experience");
  });

  it("does not refetch merely because useWorkAssignments' resolved length changed (the F10 bug: the contract query used to be serialised behind assignments)", async () => {
    get.mockResolvedValue({ data: { id: "hid-1", type: "session-experience", attributes: { authenticated: true } } });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    function TestWrapper({ children }: { children: ReactNode }) {
      return React.createElement(QueryClientProvider, { client }, children);
    }

    const { result, rerender } = renderHook(() => useSessionExperienceContract(), { wrapper: TestWrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(get).toHaveBeenCalledTimes(1);

    // Assignments resolve from empty to populated — a query keyed on workAssignments.length
    // would refetch here; the fixed hook must not.
    useWorkAssignmentsMock.mockReturnValue({ data: [{ id: "a1" }, { id: "a2" }] });
    rerender();

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(get).toHaveBeenCalledTimes(1);
  });
});
