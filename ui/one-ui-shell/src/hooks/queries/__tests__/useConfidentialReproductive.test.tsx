/**
 * W13-B — confidential reproductive hook contract: empty list is withhold semantics;
 * 502 PCT_UNAVAILABLE is distinguishable from an empty lane.
 */

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
  isReproductiveReadUnavailable,
  REPRODUCTIVE_SELF,
  useContraceptionCoverage,
  useContraceptionHistory,
  usePregnancyLosses,
  useTopAuthorisations,
} from "../useConfidentialReproductive";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

beforeEach(() => {
  get.mockReset();
});

describe("isReproductiveReadUnavailable", () => {
  it("recognises PCT_UNAVAILABLE on 502 as an outage, not an empty lane", () => {
    expect(
      isReproductiveReadUnavailable({ status: 502, error: { code: "PCT_UNAVAILABLE" } }),
    ).toBe(true);
  });

  it("does not treat an empty 200 or unrelated errors as unavailable", () => {
    expect(isReproductiveReadUnavailable({ status: 404 })).toBe(false);
    expect(isReproductiveReadUnavailable({ status: 502, error: { code: "OTHER" } })).toBe(false);
    expect(isReproductiveReadUnavailable(null)).toBe(false);
  });
});

describe("useContraceptionCoverage", () => {
  it("reads current coverage via 'me' for citizens", async () => {
    get.mockResolvedValue({
      data: [{ contraceptive_episode_id: "C-1", subject_cpid: "CP-1", method_code: "IMPALN" }],
    });

    const { result } = renderHook(() => useContraceptionCoverage(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/confidential/reproductive/contraception/me");
    expect(result.current.data).toHaveLength(1);
  });

  it("passes asOf when supplied for clinician retrospective coverage", async () => {
    get.mockResolvedValue({ data: [] });

    renderHook(() => useContraceptionCoverage("CP-9", { asOf: "2025-06-01" }), { wrapper });

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(
        "/internal/v1/confidential/reproductive/contraception/CP-9?asOf=2025-06-01",
      ),
    );
  });

  it("treats null data as an empty withhold list, not an error", async () => {
    get.mockResolvedValue({ data: null });

    const { result } = renderHook(() => useContraceptionCoverage(REPRODUCTIVE_SELF), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });
});

describe("clinician reproductive reads", () => {
  it("fetches contraception history for a patient CPID", async () => {
    get.mockResolvedValue({ data: [{ contraceptive_episode_id: "H-1", subject_cpid: "CP-2" }] });

    const { result } = renderHook(() => useContraceptionHistory("CP-2"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/contraception/CP-2/history",
    );
  });

  it("fetches pregnancy losses for a mother CPID", async () => {
    get.mockResolvedValue({ data: [{ loss_record_id: "L-1", mother_cpid: "CP-3" }] });

    const { result } = renderHook(() => usePregnancyLosses("CP-3"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/confidential/reproductive/losses/CP-3");
  });

  it("fetches TOP authorisations for a subject CPID", async () => {
    get.mockResolvedValue({ data: [{ authorisation_id: "A-1", subject_cpid: "CP-4" }] });

    const { result } = renderHook(() => useTopAuthorisations("CP-4"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/top-authorisations/CP-4",
    );
  });
});
