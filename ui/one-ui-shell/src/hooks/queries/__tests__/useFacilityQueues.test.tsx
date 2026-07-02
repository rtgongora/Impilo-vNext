// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const getMock = vi.fn();
const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...a: unknown[]) => getMock(...a),
    post: (...a: unknown[]) => postMock(...a),
  },
}));

import {
  useQueueMaterializationStatus,
  useFacilityQueuesAdmin,
  useReconcileQueues,
} from "../useFacilityQueues";

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "TestQueryClientWrapper";
  return Wrapper;
}

describe("useFacilityQueues hooks", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
  });

  it("materialization status hits the admin route", async () => {
    getMock.mockResolvedValue({ data: { overall: "MATERIALIZED" } });
    const { result } = renderHook(() => useQueueMaterializationStatus("fac-1"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith(
      "/internal/v1/admin/facilities/fac-1/queues/materialization-status",
    );
  });

  it("facility queues hits the admin route", async () => {
    getMock.mockResolvedValue({ data: [] });
    const { result } = renderHook(() => useFacilityQueuesAdmin("fac-1"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/fac-1/queues");
  });

  it("reconcile posts to the admin reconcile route", async () => {
    postMock.mockResolvedValue({ data: { status: "OK", created: 2 } });
    const { result } = renderHook(() => useReconcileQueues("fac-1"), { wrapper: wrapper() });
    await result.current.mutateAsync();
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/fac-1/queues/reconcile");
  });
});
