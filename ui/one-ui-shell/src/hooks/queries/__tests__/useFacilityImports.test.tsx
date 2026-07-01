// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

const getMock = vi.fn();
const postMock = vi.fn();
const patchMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    patch: (...args: unknown[]) => patchMock(...args),
  },
}));

import {
  useFacilityImportRuns,
  useFacilityImportRun,
  useFacilityProvenance,
  useFacilityImportRows,
  useFacilityImportDuplicates,
  useSupplyCode,
  useApproveRow,
  useApplyApproved,
  useUpdateCanonical,
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
    postMock.mockReset();
    patchMock.mockReset();
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

  it("useFacilityImportRows builds the filtered/paginated rows path", async () => {
    getMock.mockResolvedValue({ data: { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } });
    const { result } = renderHook(
      () => useFacilityImportRows("7", { outcome: "IMPORTED", page: 0, size: 25 }),
      { wrapper: wrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith(
      "/internal/v1/admin/facility-import-runs/7/rows?outcome=IMPORTED&page=0&size=25",
    );
  });

  it("useFacilityImportDuplicates hits the duplicates route with type", async () => {
    getMock.mockResolvedValue({ data: { runId: 7, duplicateType: "FACILITY_NAME", groupCount: 0, groups: [] } });
    const { result } = renderHook(() => useFacilityImportDuplicates("7", "FACILITY_NAME"), {
      wrapper: wrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getMock).toHaveBeenCalledWith(
      "/internal/v1/admin/facility-import-runs/7/duplicates?type=FACILITY_NAME",
    );
  });

  it("useSupplyCode posts the code to the supply-code route", async () => {
    postMock.mockResolvedValue({ data: { id: 1, decisionStatus: "CORRECTED_READY_FOR_IMPORT" } });
    const { result } = renderHook(() => useSupplyCode("7"), { wrapper: wrapper() });
    await result.current.mutateAsync({ rowId: 1, facilityCode: "ZWX1", reason: "r" });
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facility-import-runs/7/rows/1/supply-code", {
      facilityCode: "ZWX1",
      reason: "r",
    });
  });

  it("useApproveRow posts to the approve route", async () => {
    postMock.mockResolvedValue({ data: { id: 1, decisionStatus: "APPROVED_FOR_IMPORT" } });
    const { result } = renderHook(() => useApproveRow("7"), { wrapper: wrapper() });
    await result.current.mutateAsync({ rowId: 9 });
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facility-import-runs/7/rows/9/approve");
  });

  it("useApplyApproved posts rowIds to apply-approved", async () => {
    postMock.mockResolvedValue({ data: { applied: 2, skipped: 0 } });
    const { result } = renderHook(() => useApplyApproved("7"), { wrapper: wrapper() });
    await result.current.mutateAsync({ rowIds: [1, 2] });
    expect(postMock).toHaveBeenCalledWith("/internal/v1/admin/facility-import-runs/7/apply-approved", {
      rowIds: [1, 2],
    });
  });

  it("useUpdateCanonical patches canonical-values", async () => {
    patchMock.mockResolvedValue({ data: { id: 1 } });
    const { result } = renderHook(() => useUpdateCanonical("7"), { wrapper: wrapper() });
    await result.current.mutateAsync({ rowId: 3, values: { facilityName: "New Name" } });
    expect(patchMock).toHaveBeenCalledWith(
      "/internal/v1/admin/facility-import-runs/7/rows/3/canonical-values",
      { facilityName: "New Name" },
    );
  });
});
