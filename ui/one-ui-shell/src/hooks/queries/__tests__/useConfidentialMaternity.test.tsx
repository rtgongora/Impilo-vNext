/**
 * W12 — confidential-maternity hook contract: `dangerSignPresent` stays optional (never coerced
 * to false), a 502 series-outage is distinguishable from INSUFFICIENT_DATA, and pregnancy booking
 * treats 409 as a reconciliation outcome rather than a masked failure.
 */

import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

import {
  isPregnancyConflict,
  isPregnancyUndatable,
  isSmbpUnavailableError,
  useCurrentPregnancyEpisode,
  useOpenPregnancyEpisode,
  usePregnancyEpisodes,
  useSmbpVerdict,
} from "../useConfidentialMaternity";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe("useSmbpVerdict", () => {
  it("omits dangerSignPresent when it was never asked (never defaults to false)", async () => {
    get.mockResolvedValue({
      data: { verdict: "INSUFFICIENT_DATA", message: "m", note: "n", summary: {}, alerts: [] },
      meta: { plan_id: "P-1", raw_reading_count: 2, paired_reading_count: 1 },
    });

    const { result } = renderHook(() => useSmbpVerdict("P-1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/confidential/maternity/smbp/verdict?planId=P-1");
    expect(result.current.data?.data.verdict).toBe("INSUFFICIENT_DATA");
  });

  it("sends dangerSignPresent explicitly when an answer exists (true or false)", async () => {
    get.mockResolvedValue({ data: { verdict: "URGENT" }, meta: {} });

    renderHook(() => useSmbpVerdict("P-1", false), { wrapper });

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(
        "/internal/v1/confidential/maternity/smbp/verdict?planId=P-1&dangerSignPresent=false",
      ),
    );
  });

  it("does not fire without a planId", () => {
    renderHook(() => useSmbpVerdict(undefined), { wrapper });
    expect(get).not.toHaveBeenCalled();
  });
});

describe("isSmbpUnavailableError (§6a — never render as INSUFFICIENT_DATA or controlled)", () => {
  it("recognises READINGS_UNAVAILABLE and CKP_UNAVAILABLE as a 502 outage", () => {
    expect(
      isSmbpUnavailableError({ status: 502, error: { code: "READINGS_UNAVAILABLE" } }),
    ).toBe(true);
    expect(isSmbpUnavailableError({ status: 502, error: { code: "CKP_UNAVAILABLE" } })).toBe(true);
  });

  it("does not mistake an unrelated error or a real verdict for the outage", () => {
    expect(isSmbpUnavailableError({ status: 400, error: { code: "BAD_REQUEST" } })).toBe(false);
    expect(isSmbpUnavailableError({ status: 502, error: { code: "PCT_UNAVAILABLE" } })).toBe(false);
    expect(isSmbpUnavailableError(null)).toBe(false);
  });
});

describe("useCurrentPregnancyEpisode / usePregnancyEpisodes (browser holds no CPID)", () => {
  it("reads her current pregnancy via the 'me' sentinel, not a client-held CPID", async () => {
    get.mockResolvedValue({ data: { pregnancy_episode_id: "E-1", status: "ONGOING" } });

    const { result } = renderHook(() => useCurrentPregnancyEpisode(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/confidential/maternity/pregnancy-episodes/me/current",
    );
    expect(result.current.data?.pregnancy_episode_id).toBe("E-1");
  });

  it("treats a null data body as no current pregnancy, not an error", async () => {
    get.mockResolvedValue({ data: null });

    const { result } = renderHook(() => useCurrentPregnancyEpisode(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });

  it("reads her history via the same sentinel", async () => {
    get.mockResolvedValue({ data: [{ pregnancy_episode_id: "E-0", status: "ENDED" }] });

    const { result } = renderHook(() => usePregnancyEpisodes(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/confidential/maternity/pregnancy-episodes/me");
    expect(result.current.data).toHaveLength(1);
  });
});

describe("useOpenPregnancyEpisode (201/200/409/422 semantics)", () => {
  it("resolves a fresh booking (201) as success", async () => {
    post.mockResolvedValue({
      data: { pregnancy_episode_id: "E-9", status: "ONGOING", replayed: false },
    });

    const { result } = renderHook(() => useOpenPregnancyEpisode(), { wrapper });
    result.current.mutate({ datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-05-01" }] });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/confidential/maternity/pregnancy-episodes", {
      datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-05-01" }],
    });
    expect(result.current.data?.replayed).toBe(false);
  });

  it("resolves a replayed offline packet (200) as success, not a duplicate", async () => {
    post.mockResolvedValue({
      data: { pregnancy_episode_id: "E-9", status: "ONGOING", replayed: true },
    });

    const { result } = renderHook(() => useOpenPregnancyEpisode(), { wrapper });
    result.current.mutate({
      datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-05-01" }],
      clientOfflineId: "offline-1",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.replayed).toBe(true);
  });

  it("surfaces a 409 as a reconciliation outcome, never a masked generic failure", async () => {
    post.mockRejectedValue({
      status: 409,
      data: {
        code: "PREGNANCY_ALREADY_BOOKED",
        message: "already pregnant",
        existing_pregnancy_episode_id: "E-EXISTING",
        reconciliation_task: "Open the existing episode and reconcile.",
      },
      meta: { correlation_id: "corr-1" },
    });

    const { result } = renderHook(() => useOpenPregnancyEpisode(), { wrapper });
    result.current.mutate({ datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-05-01" }] });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isPregnancyConflict(result.current.error)).toBe(true);
    expect(isPregnancyUndatable(result.current.error)).toBe(false);
    if (isPregnancyConflict(result.current.error)) {
      expect(result.current.error.data?.existing_pregnancy_episode_id).toBe("E-EXISTING");
    }
  });

  it("surfaces a 422 as undatable, distinct from a 409 conflict", async () => {
    post.mockRejectedValue({
      status: 422,
      data: { code: "PREGNANCY_UNDATABLE", message: "no dating basis" },
    });

    const { result } = renderHook(() => useOpenPregnancyEpisode(), { wrapper });
    result.current.mutate({ datingBases: [] });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(isPregnancyUndatable(result.current.error)).toBe(true);
    expect(isPregnancyConflict(result.current.error)).toBe(false);
  });
});
