// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const getMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { get: (...args: unknown[]) => getMock(...args) },
}));

import {
  useFacilityImportRuns,
  useFacilityImportRun,
  useFacilityProvenance,
} from "../useFacilityImports";

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe("useFacilityImports hooks", () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it("useFacilityImportRuns hits the admin runs route", async () => {
    getMock.mockResolvedValue({ data: { count: 0, runs: [] } });
    const { result } = renderHook(() => useFacilityImportRuns(), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facility-import-runs");
  });

  it("useFacilityImportRun hits the run detail route", async () => {
    getMock.mockResolvedValue({ data: { runId: 7 } });
    const { result } = renderHook(() => useFacilityImportRun("7"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facility-import-runs/7");
  });

  it("useFacilityProvenance hits the admin provenance route (not the facility code)", async () => {
    getMock.mockResolvedValue({ data: { identity: {} } });
    const { result } = renderHook(() => useFacilityProvenance("55"), { wrapper: wrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith("/internal/v1/admin/facilities/55/import-provenance");
  });
});
