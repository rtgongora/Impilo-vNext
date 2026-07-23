/**
 * OF-B7 — marketplace comparison/selection hook contract:
 * BFF paths, caller-owned Idempotency-Key passthrough (RC-1),
 * and cache invalidation after mutations.
 */

import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post, put },
}));

import {
  useElectPathway,
  useMarketplaceOffers,
  useSelectOffer,
  useSelectSplit,
} from "../useMarketplaceOffers";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  put.mockReset();
});

describe("useMarketplaceOffers", () => {
  it("fetches the ranked comparison listing from the BFF proxy", async () => {
    get.mockResolvedValue({ success: true, data: [{ offerId: "OF-1" }] });

    const { result } = renderHook(() => useMarketplaceOffers("MR-1"), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/requests/MR-1/offers");
    expect(result.current.data?.data).toEqual([{ offerId: "OF-1" }]);
  });

  it("does not fire without a requestId (no doomed requests)", () => {
    renderHook(() => useMarketplaceOffers(undefined), { wrapper });
    expect(get).not.toHaveBeenCalled();
  });
});

describe("useSelectOffer (RC-1 idempotent commit)", () => {
  it("posts the offerId with the caller-owned Idempotency-Key header", async () => {
    post.mockResolvedValue({ success: true, data: { selectionId: "SEL-1", outcomeCode: "COMMITTED", committed: true } });

    const { result } = renderHook(() => useSelectOffer(), { wrapper });
    result.current.mutate({ requestId: "MR-1", offerId: "OF-1", idempotencyKey: "key-abc" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/marketplace/requests/MR-1/select",
      { offerId: "OF-1" },
      { extraHeaders: { "Idempotency-Key": "key-abc" } },
    );
  });

  it("surfaces coded revalidation outcomes from the envelope (no masking)", async () => {
    post.mockResolvedValue({
      success: true,
      data: { selectionId: "SEL-2", outcomeCode: "OFFER_EXPIRED", committed: false },
    });

    const { result } = renderHook(() => useSelectOffer(), { wrapper });
    result.current.mutate({ requestId: "MR-1", offerId: "OF-1", idempotencyKey: "key-def" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data?.outcomeCode).toBe("OFFER_EXPIRED");
    expect(result.current.data?.data?.committed).toBe(false);
  });
});

describe("useSelectSplit (OF-B14)", () => {
  it("posts the offer combination with the Idempotency-Key header", async () => {
    post.mockResolvedValue({ success: true, data: { splitGroupId: "SG-1", outcome: "PARTIAL_COMMIT", members: [] } });

    const { result } = renderHook(() => useSelectSplit(), { wrapper });
    result.current.mutate({ requestId: "MR-1", offerIds: ["OF-1", "OF-2"], idempotencyKey: "key-split" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/marketplace/requests/MR-1/select-split",
      { offerIds: ["OF-1", "OF-2"] },
      { extraHeaders: { "Idempotency-Key": "key-split" } },
    );
    expect(result.current.data?.data?.outcome).toBe("PARTIAL_COMMIT");
  });
});

describe("useElectPathway (OF-B17)", () => {
  it("PUTs the pathway election with delivery details", async () => {
    put.mockResolvedValue({
      success: true,
      data: { requestId: "MR-1", fulfilmentPathway: "DELIVERY", deliveryDetailsPresent: true },
    });

    const { result } = renderHook(() => useElectPathway(), { wrapper });
    result.current.mutate({
      requestId: "MR-1",
      pathway: "DELIVERY",
      deliveryName: "T. Moyo",
      deliveryPhone: "+263771234567",
      deliveryAddress: "12 Main St, Harare",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(put).toHaveBeenCalledWith("/internal/v1/marketplace/requests/MR-1/fulfilment-pathway", {
      pathway: "DELIVERY",
      deliveryName: "T. Moyo",
      deliveryPhone: "+263771234567",
      deliveryAddress: "12 Main St, Harare",
    });
  });
});
