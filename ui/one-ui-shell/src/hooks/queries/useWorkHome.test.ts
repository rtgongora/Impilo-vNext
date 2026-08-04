import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useWorkHome, useWorkHomeSectionRetry, type WorkHomeAttributes } from "./useWorkHome";

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

const WORK_HOME_ATTRS: WorkHomeAttributes = {
  contextId: "ctx-1",
  family: "FACILITY_CLINICAL",
  mode: "CLINICAL_CARE",
  friendlyState: "",
  sections: [
    {
      sectionId: "clinical-worklist",
      title: "Clinical worklist",
      status: "OK",
      items: [{ id: "1", title: "See patient", priority: "URGENT" }],
      buckets: { ACT_NOW: [{ id: "1", title: "See patient", priority: "URGENT" }] },
      summary: { total: 1 },
      generatedAt: "2026-07-28T05:00:00Z",
    },
  ],
};

describe("useWorkHome", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("does not call the BFF when no contextId is provided", () => {
    renderHook(() => useWorkHome(undefined), { wrapper });
    expect(get).not.toHaveBeenCalled();
  });

  it("GETs /internal/v1/work-home with context_id when a contextId is provided", async () => {
    get.mockResolvedValue({ data: { id: "hid-1", type: "work-home", attributes: WORK_HOME_ATTRS } });

    const { result } = renderHook(() => useWorkHome("ctx-1"), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(get).toHaveBeenCalledWith("/internal/v1/work-home?context_id=ctx-1");
    expect(result.current.workHome.sections).toHaveLength(1);
    expect(result.current.workHome.sections[0].sectionId).toBe("clinical-worklist");
  });

  it("includes mode in the query string when provided", async () => {
    get.mockResolvedValue({ data: { id: "hid-1", type: "work-home", attributes: WORK_HOME_ATTRS } });

    renderHook(() => useWorkHome("ctx-1", "CLINICAL_CARE"), { wrapper });

    await waitFor(() => expect(get).toHaveBeenCalledWith("/internal/v1/work-home?context_id=ctx-1&mode=CLINICAL_CARE"));
  });

  it("a failed READ surfaces as readFailed — never as a server verdict (item 72 / A81)", async () => {
    // This test previously pinned the DEFECT: a network error was converted
    // into friendlyState "work_context_unavailable", so a BFF 502 rendered as
    // "the assignment could not be re-proven from its source" — a verdict the
    // server never issued. The honest contract: read failure ⇒ readFailed,
    // and NO fabricated friendlyState.
    get.mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useWorkHome("ctx-1"), { wrapper });

    await waitFor(() => expect(result.current.readFailed).toBe(true), { timeout: 4000 });
    expect(result.current.workHome.friendlyState).toBe("");
    expect(result.current.workHome.friendlyState).not.toBe("work_context_unavailable");
    expect(result.current.workHome.sections).toEqual([]);
  });

  it("a successful read with zero sections is EMPTY, not readFailed", async () => {
    get.mockResolvedValue({
      data: { id: "hid-1", type: "work-home", attributes: { ...WORK_HOME_ATTRS, sections: [] } },
    });

    const { result } = renderHook(() => useWorkHome("ctx-1"), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.readFailed).toBe(false);
    expect(result.current.workHome.sections).toEqual([]);
    expect(result.current.workHome.friendlyState).toBe("");
  });
});

describe("useWorkHomeSectionRetry", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("does nothing when no contextId is set", async () => {
    const client = new QueryClient();
    function TestWrapper({ children }: { children: ReactNode }) {
      return React.createElement(QueryClientProvider, { client }, children);
    }
    const { result } = renderHook(() => useWorkHomeSectionRetry(undefined), { wrapper: TestWrapper });
    await result.current("clinical-worklist");
    expect(get).not.toHaveBeenCalled();
  });

  it("GETs the single section and merges it back into the cached work-home data", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    function TestWrapper({ children }: { children: ReactNode }) {
      return React.createElement(QueryClientProvider, { client }, children);
    }

    client.setQueryData(["work-home", "ctx-1", undefined], WORK_HOME_ATTRS);

    const retried = {
      ...WORK_HOME_ATTRS.sections[0],
      status: "OK" as const,
      note: null,
    };
    get.mockResolvedValue({
      data: { id: "clinical-worklist", type: "work-home-section", attributes: retried },
    });

    const { result } = renderHook(() => useWorkHomeSectionRetry("ctx-1"), { wrapper: TestWrapper });
    await result.current("clinical-worklist");

    expect(get).toHaveBeenCalledWith("/internal/v1/work-home/sections/clinical-worklist?context_id=ctx-1");
    const updated = client.getQueryData<WorkHomeAttributes>(["work-home", "ctx-1", undefined]);
    expect(updated?.sections[0].status).toBe("OK");
  });
});
