import type { ReactNode } from "react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  requirementCodesKey,
  useResolveRequirementSourcing,
  useSourcingCategories,
  useSupplierDemand,
  useVerifyWholesalerLicence,
} from "../useRequirementSourcing";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

/** One QueryClient per test — stable across re-renders so mutation rejections stay owned. */
function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function wrapper({ children }: { children: ReactNode }) {
    return React.createElement(QueryClientProvider, { client }, children);
  };
}

describe("requirementCodesKey", () => {
  it("is stable across ordering and duplicates", () => {
    expect(requirementCodesKey(["B-2", "A-1", "B-2"])).toBe("A-1|B-2");
    expect(requirementCodesKey(["A-1", "B-2"])).toBe(requirementCodesKey(["B-2", "A-1"]));
    expect(requirementCodesKey([])).toBe("");
  });
});

describe("useSourcingCategories", () => {
  beforeEach(() => get.mockReset());

  it("GETs the sourcing category register", async () => {
    get.mockResolvedValue({
      data: {
        categories: [
          {
            code: "COLD_CHAIN",
            label: "Cold chain equipment",
            description: null,
            restricted: false,
            requiredCredential: null,
            publishedListingCount: 4,
          },
        ],
      },
      meta: { request_id: "r", correlation_id: "c" },
    });

    const { result } = renderHook(() => useSourcingCategories(), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/sourcing/categories");
    expect(result.current.data?.data.categories[0].code).toBe("COLD_CHAIN");
  });
});

describe("useResolveRequirementSourcing", () => {
  beforeEach(() => post.mockReset());

  it("POSTs a de-duplicated sorted code batch and keeps unmapped codes absent", async () => {
    post.mockResolvedValue({
      data: {
        resolutions: {
          "GEN-001": {
            requirementCode: "GEN-001",
            categoryCode: "PPE",
            categoryLabel: "Personal protective equipment",
            restricted: false,
            requiredCredential: null,
            publishedListingCount: 7,
            marketplaceLink: "/marketplace/store?category=PPE",
          },
        },
      },
      meta: { request_id: "r", correlation_id: "c" },
    });

    const { result } = renderHook(
      () => useResolveRequirementSourcing(["GEN-002", "GEN-001", "GEN-002"]),
      { wrapper: createWrapper() }
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith("/internal/v1/marketplace/sourcing/requirements/resolve", {
      codes: ["GEN-001", "GEN-002"],
    });
    const resolutions = result.current.data!.data.resolutions;
    expect(resolutions["GEN-001"].categoryCode).toBe("PPE");
    // Unmapped codes are absent by design — no fabricated entries.
    expect(resolutions["GEN-002"]).toBeUndefined();
  });

  it("stays disabled for an empty code list", () => {
    const { result } = renderHook(() => useResolveRequirementSourcing([]), { wrapper: createWrapper() });
    expect(result.current.fetchStatus).toBe("idle");
    expect(post).not.toHaveBeenCalled();
  });
});

describe("useSupplierDemand", () => {
  beforeEach(() => get.mockReset());

  it("GETs the aggregated supplier-demand envelope", async () => {
    get.mockResolvedValue({
      data: {
        generatedAt: "2026-07-11T00:00:00Z",
        smallCellFloor: 5,
        categories: [
          {
            categoryCode: "PPE",
            categoryLabel: "Personal protective equipment",
            restricted: false,
            openFindings: 12,
            openComplianceActions: 3,
            byProvince: { Harare: 8, Manicaland: 4 },
          },
        ],
        openApplicationsByFacilityType: [{ facilityType: "SURGERY", count: 6 }],
      },
      meta: { request_id: "r", correlation_id: "c" },
    });

    const { result } = renderHook(() => useSupplierDemand(), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(get).toHaveBeenCalledWith("/internal/v1/marketplace/sourcing/supplier-demand");
    expect(result.current.data?.data.smallCellFloor).toBe(5);
    expect(result.current.data?.data.categories[0].byProvince.Harare).toBe(8);
  });
});

describe("useVerifyWholesalerLicence", () => {
  beforeEach(() => post.mockReset());

  it("POSTs the verification code to the storefront verifier", async () => {
    post.mockResolvedValue({ data: { storefrontId: "SF-1", wholesalerVerified: true } });

    const { result } = renderHook(() => useVerifyWholesalerLicence(), { wrapper: createWrapper() });
    result.current.mutate({ storefrontId: "SF-1", verificationCode: "WHL-999" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(post).toHaveBeenCalledWith(
      "/internal/v1/marketplace/seller/storefront/SF-1/verify-wholesaler-licence",
      { verificationCode: "WHL-999" },
    );
  });

  it("surfaces a 422 failure as an error result", async () => {
    const failure = Object.assign(new Error("Verification failed"), {
      body: { error: "LICENCE_INVALID", message: "Verification code does not match" },
    });
    post.mockRejectedValueOnce(failure);

    const { result } = renderHook(() => useVerifyWholesalerLicence(), { wrapper: createWrapper() });
    result.current.mutate({ storefrontId: "SF-1", verificationCode: "BAD" });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBe(failure);
  });
});
