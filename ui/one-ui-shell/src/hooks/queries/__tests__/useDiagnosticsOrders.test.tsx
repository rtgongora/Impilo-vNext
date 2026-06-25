import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  useDiagnosticsOrders,
  useResultsInbox,
  useDiagnosticsReconcileSummary,
  useCreateDiagnosticOrder,
  useRouteDiagnosticOrder,
  useCriticalUnacknowledged,
  useAcknowledgeCritical,
} from "../useDiagnosticsOrders";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

describe("useDiagnosticsOrders", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

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

  it("create order posts a draft then submits it", async () => {
    post
      .mockResolvedValueOnce({ data: { orderId: "ORD-NEW", status: "DRAFT" } })
      .mockResolvedValueOnce({ data: { orderId: "ORD-NEW", status: "PLACED", accessionNumber: "ACC-1" } });

    const { result } = renderHook(() => useCreateDiagnosticOrder(), { wrapper });
    result.current.mutate({
      patientCpid: "CPID-1",
      orderType: "IMAGING",
      items: [{ code: "CHEST-XR", modality: "XR" }],
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.accessionNumber).toBe("ACC-1");
    expect(post).toHaveBeenNthCalledWith(1, "/internal/v1/diagnostics/orders/draft", expect.objectContaining({
      orderType: "IMAGING",
      patientCpid: "CPID-1",
      requestSource: "INTERNAL",
    }));
    expect(post).toHaveBeenNthCalledWith(2, "/internal/v1/diagnostics/orders/ORD-NEW/submit");
  });

  it("route order posts the destination to the route endpoint", async () => {
    post.mockResolvedValue({ data: { orderId: "ORD-1" } });

    const { result } = renderHook(() => useRouteDiagnosticOrder(), { wrapper });
    result.current.mutate({
      orderId: "ORD-1",
      destinationType: "EXTERNAL_PROVIDER",
      destinationProviderId: "prov-9",
      destinationName: "City Radiology",
      expectedReturnMethod: "SECURE_LINK",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/diagnostics/orders/ORD-1/route",
      expect.objectContaining({
        destinationType: "EXTERNAL_PROVIDER",
        destinationProviderId: "prov-9",
        expectedReturnMethod: "SECURE_LINK",
      }),
    );
  });

  it("critical-unacknowledged hits the dashboard endpoint", async () => {
    get.mockResolvedValue({ data: [{ resultId: "r-1", orderId: "ORD-1", critical: true }] });

    const { result } = renderHook(() => useCriticalUnacknowledged(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(get).toHaveBeenCalledWith("/internal/v1/diagnostics/critical-unacknowledged");
  });

  it("acknowledge critical posts to the critical-ack endpoint", async () => {
    post.mockResolvedValue({ data: { resultId: "r-1" } });

    const { result } = renderHook(() => useAcknowledgeCritical(), { wrapper });
    result.current.mutate({ resultId: "r-1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/diagnostics/results/r-1/critical/ack", {});
  });
});
