import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { coverageListRows, useRunCoverageCommand } from "../useCoverage";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

/**
 * Phase 6 / Slice 6A — envelope-tolerance unit test for the shared
 * coverage list-row extractor used by the four new read-only hooks:
 *   - useCoverageContributionsList
 *   - useCoveragePreauthsList
 *   - useCoverageUtilizationList
 *   - useCoverageAppealsList
 *
 * The helper is intentionally permissive: callers see either a top-level
 * array, a `{ data: [] }` envelope, a `{ items: [] }` envelope, or a
 * `{ content: [] }` envelope. Anything else returns `[]` so the UI does
 * not invent rows it has no upstream evidence for.
 */
describe("coverageListRows (Phase 6 / Slice 6A)", () => {
  it("returns top-level arrays unchanged", () => {
    expect(coverageListRows([1, 2, 3])).toEqual([1, 2, 3]);
    expect(coverageListRows([])).toEqual([]);
  });

  it("unwraps `{ data: [] }` envelopes", () => {
    expect(coverageListRows({ data: [{ a: 1 }] })).toEqual([{ a: 1 }]);
    expect(coverageListRows({ data: [] })).toEqual([]);
  });

  it("unwraps `{ items: [] }` envelopes", () => {
    expect(coverageListRows({ items: [{ id: "x" }] })).toEqual([{ id: "x" }]);
  });

  it("unwraps `{ content: [] }` envelopes (Spring-style)", () => {
    expect(coverageListRows({ content: [{ id: "y" }], totalElements: 1 })).toEqual([{ id: "y" }]);
  });

  it("returns [] for null / undefined / primitives (no fabrication)", () => {
    expect(coverageListRows(null)).toEqual([]);
    expect(coverageListRows(undefined)).toEqual([]);
    expect(coverageListRows("string")).toEqual([]);
    expect(coverageListRows(42)).toEqual([]);
    expect(coverageListRows(true)).toEqual([]);
  });

  it("returns [] for objects whose data/items/content is not an array", () => {
    expect(coverageListRows({ data: "not an array" })).toEqual([]);
    expect(coverageListRows({ items: null })).toEqual([]);
    expect(coverageListRows({ content: 7 })).toEqual([]);
    expect(coverageListRows({ totalElements: 5 })).toEqual([]);
  });
});

describe("useRunCoverageCommand", () => {
  beforeEach(() => {
    post.mockReset();
    post.mockResolvedValue({ data: { accepted: true } });
  });

  it("routes appeal submissions to the canonical appeals endpoint", async () => {
    const { result } = renderHook(() => useRunCoverageCommand(), { wrapper });

    result.current.mutate({
      command: "appeal-submission",
      payload: { claimId: "claim-1", appellantId: "cpid-1", reason: "Denied in error" },
    });

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/appeals",
      {
        claimId: "claim-1",
        appellantId: "cpid-1",
        reason: "Denied in error",
      },
      undefined,
    );
  });
});
