import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

import { isMaternitySummaryUnavailable, useMaternitySummary } from "./useMaternitySummary";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useMaternitySummary", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("is disabled without a patientId", () => {
    const { result } = renderHook(() => useMaternitySummary(undefined), { wrapper });
    expect(result.current.fetchStatus).toBe("idle");
    expect(get).not.toHaveBeenCalled();
  });

  it("requests the summary with patientId and an optional encounterId", async () => {
    get.mockResolvedValue({
      data: { patient_id: "P1", partograph_active: false, ctg_active: false, observation_count: 0, last_observed_at: null },
      meta: {},
    });
    const { result } = renderHook(() => useMaternitySummary("P1", "E1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/maternity/summary?patientId=P1&encounterId=E1");
  });

  it("surfaces partograph/ctg activity and observation counts", async () => {
    get.mockResolvedValue({
      data: {
        patient_id: "P1",
        partograph_active: true,
        partograph_session_id: "S1",
        progress: { status: "LEFT_OF_ALERT" },
        ctg_active: true,
        ctg_session_id: "S2",
        observation_count: 4,
        last_observed_at: "2026-07-30T10:00:00Z",
      },
      meta: {},
    });
    const { result } = renderHook(() => useMaternitySummary("P1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.partograph_active).toBe(true);
    expect(result.current.data?.data.progress?.status).toBe("LEFT_OF_ALERT");
    expect(result.current.data?.data.observation_count).toBe(4);
  });

  it("propagates the 502 maternity_summary_unavailable refusal, never an empty record", async () => {
    get.mockRejectedValue({
      status: 502,
      error: "maternity_summary_unavailable",
      message: "The maternity summary could not be retrieved.",
      meta: {},
    });
    const { result } = renderHook(() => useMaternitySummary("P1"), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isMaternitySummaryUnavailable(result.current.error)).toBe(true);
  });

  it("isMaternitySummaryUnavailable is false for an unrelated error", () => {
    expect(isMaternitySummaryUnavailable({ status: 404 })).toBe(false);
    expect(isMaternitySummaryUnavailable(null)).toBe(false);
  });
});
