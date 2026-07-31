import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

import {
  isBirthDestinationUnavailable,
  useBirthDestination,
} from "./useBirthDestination";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useBirthDestination", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("requests the BFF endpoint with facilityId and requiredLevel as query params", async () => {
    get.mockResolvedValue({
      data: {
        facility_id: 42,
        required_level: "BEMONC",
        status: "MEETS_REQUIRED_LEVEL",
        operational_gate_passed: true,
        emonc_verdict: "BEMONC",
        call_ahead: true,
        message: "This facility meets the level of care she needs.",
        guidance: "Confirm by phone that it is ready to receive her.",
      },
      meta: { request_id: "r1", correlation_id: "c1" },
    });
    const { result } = renderHook(() => useBirthDestination(), { wrapper });

    await result.current.mutateAsync({ facilityId: 42, requiredLevel: "BEMONC" });

    expect(get).toHaveBeenCalledWith("/internal/v1/maternity/birth-destination?facilityId=42&requiredLevel=BEMONC");
  });

  it("resolves CAPABILITY_UNKNOWN as a real answer, not a thrown error", async () => {
    get.mockResolvedValue({
      data: {
        facility_id: 7,
        required_level: "CEMONC",
        status: "CAPABILITY_UNKNOWN",
        operational_gate_passed: true,
        emonc_verdict: "INSUFFICIENT_EVIDENCE",
        call_ahead: true,
        message: "Nothing is known about this facility's emergency obstetric capability.",
        guidance: "This is not a statement that it has none — it has not been assessed. Call ahead.",
      },
      meta: {},
    });
    const { result } = renderHook(() => useBirthDestination(), { wrapper });

    const response = await result.current.mutateAsync({ facilityId: 7, requiredLevel: "CEMONC" });

    expect(response.data.status).toBe("CAPABILITY_UNKNOWN");
    expect(response.data.call_ahead).toBe(true);
  });

  it("propagates the 502 UNAVAILABLE refusal rather than swallowing it into success", async () => {
    get.mockRejectedValue({
      status: 502,
      data: {
        facility_id: 7,
        required_level: "UNKNOWN",
        status: "UNAVAILABLE",
        operational_gate_passed: false,
        emonc_verdict: null,
        call_ahead: true,
        message: "The facility's status could not be retrieved.",
        guidance: "Do not treat this as either a safe or an unsafe destination — it is unknown.",
      },
      meta: {},
    });
    const { result } = renderHook(() => useBirthDestination(), { wrapper });

    await expect(result.current.mutateAsync({ facilityId: 7, requiredLevel: "UNKNOWN" })).rejects.toBeTruthy();
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isBirthDestinationUnavailable(result.current.error)).toBe(true);
  });

  it("isBirthDestinationUnavailable is false for a 200 verdict-shaped object", () => {
    expect(isBirthDestinationUnavailable({ status: 200, data: {} })).toBe(false);
    expect(isBirthDestinationUnavailable(null)).toBe(false);
  });
});
