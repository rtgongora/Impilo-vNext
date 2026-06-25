import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  useDiagnosticsOrders,
  useResultsInbox,
  useDiagnosticsReconcileSummary,
} from "../useDiagnosticsOrders";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get },
}));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe("useDiagnosticsOrders", () => {
  beforeEach(() => get.mockReset());

  it("calls the BFF diagnostics orders endpoint with the type filter", async () => {
    get.mockResolvedValue({
      data: [
        {
          orderId: "ORD-1",
          orderType: "IMAGING",
          status: "PLACED",
          patientCpid: "CPID-1",
          accessionNumber: "ACC-2026-AB-1",
          imagingState: "RECEIVED",
        },
      ],
    });

    const { result } = renderHook(() => useDiagnosticsOrders({ type: "IMAGING" }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(get).toHaveBeenCalledWith("/internal/v1/diagnostics/orders?type=IMAGING");
  });

  it("results inbox passes the requester query param", async () => {
    get.mockResolvedValue({ data: [] });

    const { result } = renderHook(() => useResultsInbox("dr-9"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/diagnostics/results-inbox?requester=dr-9");
  });

  it("reconcile summary hits the summary endpoint", async () => {
    get.mockResolvedValue({ data: { RECEIVED_NOT_ACCEPTED: 2, CRITICAL_UNACKNOWLEDGED: 1 } });

    const { result } = renderHook(() => useDiagnosticsReconcileSummary(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.CRITICAL_UNACKNOWLEDGED).toBe(1);
    expect(get).toHaveBeenCalledWith("/internal/v1/diagnostics/reconcile-summary");
  });
});
