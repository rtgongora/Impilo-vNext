import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useEmergencyBundleAssess } from "./useEmergencyBundle";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { post } }));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useEmergencyBundleAssess", () => {
  beforeEach(() => post.mockReset());

  it("posts PPH assessment with control omitted when not assessed", async () => {
    // apiClient.post resolves the parsed body itself: `{ data: assessment, meta }`.
    post.mockResolvedValue({ data: { status: "ACTIVE", steps: [], outstanding_mandatory: ["PPH_MASSAGE"], may_close: false, note: "" } });
    const { result } = renderHook(() => useEmergencyBundleAssess("pph"), { wrapper });
    result.current.mutate({ completedSteps: [], minutesSinceTrigger: 0, minutesSinceLastObservation: 0, controlConfirmed: null });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/clinical/maternal/emergency-bundles/pph/assess",
      { completedSteps: [], minutesSinceTrigger: 0, minutesSinceLastObservation: 0 });
  });
});
