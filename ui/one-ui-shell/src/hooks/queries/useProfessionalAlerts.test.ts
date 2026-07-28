import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useProfessionalAlerts } from "./useProfessionalAlerts";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { user: { id: string }; isAuthenticated: boolean }) => unknown) =>
    selector({ user: { id: "hid-1" }, isAuthenticated: true }),
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useProfessionalAlerts", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("GETs /internal/v1/professional/alerts", async () => {
    get.mockResolvedValue({
      data: {
        id: "hid-1",
        type: "professional-alerts",
        attributes: { status: "OK", items: [{ id: "licence:1", title: "Licence expiring" }], summary: { total: 1 }, generatedAt: "2026-07-28T00:00:00Z" },
      },
    });

    const { result } = renderHook(() => useProfessionalAlerts(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(get).toHaveBeenCalledWith("/internal/v1/professional/alerts");
    expect(result.current.alerts.items).toHaveLength(1);
  });

  it("surfaces a failed fetch as isError (never silently folded into an EMPTY all-clear)", async () => {
    get.mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useProfessionalAlerts(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.isError).toBe(true);
    // The fallback shape is still safe to render defensively, but the caller must check isError
    // first — exactly like /professional's other sections (linkedIdsUnavailable, etc).
    expect(result.current.alerts.items).toEqual([]);
  });
});
