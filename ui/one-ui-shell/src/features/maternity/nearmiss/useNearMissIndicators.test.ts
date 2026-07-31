import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({
  apiClient: { post },
}));

import {
  isIndicatorsRejected,
  isIndicatorsUnavailable,
  parseIndicatorsRejection,
  useNearMissIndicators,
} from "./useMaternalNearMiss";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useNearMissIndicators", () => {
  beforeEach(() => {
    post.mockReset();
  });

  it("posts indicatorPeriod and cases exactly as given", async () => {
    post.mockResolvedValue({
      data: {
        indicator_period: "2026-Q2",
        near_miss_count: 2,
        maternal_death_count: 1,
        indeterminate_count: 0,
        severe_maternal_outcome_count: 3,
        near_miss_to_death_ratio: 2,
        near_miss_to_death_ratio_upper_bound: null,
        mortality_index: 33.33,
        mortality_index_lower_bound: null,
        mortality_index_direction: "HIGHER_IS_WORSE",
        note: null,
      },
      meta: {},
    });
    const { result } = renderHook(() => useNearMissIndicators(), { wrapper });

    await result.current.mutateAsync({
      indicatorPeriod: "2026-Q2",
      cases: [{ outcome: "NEAR_MISS" }, { outcome: "NEAR_MISS" }, { outcome: "MATERNAL_DEATH" }],
    });

    expect(post).toHaveBeenCalledWith("/internal/v1/clinical/maternal/near-miss/indicators", {
      indicatorPeriod: "2026-Q2",
      cases: [{ outcome: "NEAR_MISS" }, { outcome: "NEAR_MISS" }, { outcome: "MATERNAL_DEATH" }],
    });
  });

  it("resolves a null ratio and mortality index as null, not zero", async () => {
    post.mockResolvedValue({
      data: {
        indicator_period: "2026-Q3",
        near_miss_count: 1,
        maternal_death_count: 0,
        indeterminate_count: 0,
        severe_maternal_outcome_count: 1,
        near_miss_to_death_ratio: null,
        near_miss_to_death_ratio_upper_bound: 1,
        mortality_index: null,
        mortality_index_lower_bound: 0,
        mortality_index_direction: "HIGHER_IS_WORSE",
        note: "No deaths recorded — the ratio is undefined, not zero.",
      },
      meta: {},
    });
    const { result } = renderHook(() => useNearMissIndicators(), { wrapper });

    const response = await result.current.mutateAsync({ indicatorPeriod: "2026-Q3", cases: [{ outcome: "NEAR_MISS" }] });

    expect(response.data.near_miss_to_death_ratio).toBeNull();
    expect(response.data.mortality_index).toBeNull();
    expect(response.data.note).toContain("undefined, not zero");
  });

  it("propagates a 502 unavailable refusal rather than a fake computation", async () => {
    post.mockRejectedValue({
      status: 502,
      error: "near_miss_unavailable",
      message: "The near-miss criteria could not be evaluated.",
      meta: {},
    });
    const { result } = renderHook(() => useNearMissIndicators(), { wrapper });

    await expect(result.current.mutateAsync({ indicatorPeriod: "p", cases: [] })).rejects.toBeTruthy();
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isIndicatorsUnavailable(result.current.error)).toBe(true);
    expect(isIndicatorsRejected(result.current.error)).toBe(false);
  });

  it("parseIndicatorsRejection recovers the rejected cases from the forwarded upstream_body", async () => {
    const upstreamBody = JSON.stringify({
      data: {
        error: "unclassifiable_outcome",
        message: "Every case must carry a recognised outcome.",
        rejected_cases: ["case[1]: MAYBE"],
        accepted_values: ["NEAR_MISS", "MATERNAL_DEATH", "NEAR_MISS_INDETERMINATE", "NOT_SEVERE"],
      },
    });
    post.mockRejectedValue({
      status: 422,
      upstream_status: 422,
      upstream_body: upstreamBody,
      meta: {},
    });
    const { result } = renderHook(() => useNearMissIndicators(), { wrapper });

    await expect(
      result.current.mutateAsync({ indicatorPeriod: "p", cases: [{ outcome: "NEAR_MISS" }, { outcome: "MAYBE" as never }] }),
    ).rejects.toBeTruthy();
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(isIndicatorsRejected(result.current.error)).toBe(true);
    expect(isIndicatorsUnavailable(result.current.error)).toBe(false);
    const parsed = parseIndicatorsRejection(result.current.error as unknown as { upstream_body?: string; status: number });
    expect(parsed?.rejectedCases).toEqual(["case[1]: MAYBE"]);
    expect(parsed?.acceptedValues).toContain("NEAR_MISS_INDETERMINATE");
  });

  it("parseIndicatorsRejection returns null (never fabricated) when the body is unparseable", () => {
    expect(parseIndicatorsRejection({ status: 422, upstream_body: "not json" })).toBeNull();
    expect(parseIndicatorsRejection({ status: 422 })).toBeNull();
  });
});
