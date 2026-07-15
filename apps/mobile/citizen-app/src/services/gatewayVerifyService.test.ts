import { describe, it, expect, vi, beforeEach } from "vitest";

const publicClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const { MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    status: number;
    constructor(status: number) {
      super("api");
      this.status = status;
    }
  }
  return { MockApiError };
});
vi.mock("@impilo/mobile-api-client", () => ({ publicApiClient: publicClient, ApiError: MockApiError }));

import {
  verifyPractitioner,
  searchPublicFacilities,
  fetchPublicFacilityProfile,
} from "./gatewayVerifyService";

describe("gatewayVerifyService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("verifies a practitioner by registration number on the public register lane", async () => {
    publicClient.get.mockResolvedValue({
      data: { displayName: "Dr T Moyo", profession: "Doctor", registerStatus: "ACTIVE", registrationNumber: "MDCN-1" },
    });
    const res = await verifyPractitioner(" MDCN-1 ");
    expect(publicClient.get).toHaveBeenCalledWith("/internal/v1/public/gateway/practitioners/verify/MDCN-1");
    expect(res?.displayName).toBe("Dr T Moyo");
  });

  it("returns null for a uniform NOT_FOUND status (no enumeration oracle)", async () => {
    publicClient.get.mockResolvedValue({ data: { status: "NOT_FOUND" } });
    expect(await verifyPractitioner("nope")).toBeNull();
  });

  it("returns null on a 404 rather than throwing", async () => {
    publicClient.get.mockRejectedValue(new MockApiError(404));
    expect(await verifyPractitioner("nope")).toBeNull();
  });

  it("searches facilities with disclosure-limited params", async () => {
    publicClient.get.mockResolvedValue({ data: { data: [{ id: 5, name: "Parirenyatwa" }], meta: { totalElements: 1 } } });
    const res = await searchPublicFacilities({ query: "pari", province: "Harare", size: 20 });
    const url = publicClient.get.mock.calls[0][0] as string;
    expect(url).toContain("/internal/v1/public/gateway/facilities/search?");
    expect(url).toContain("query=pari");
    expect(url).toContain("province=Harare");
    expect(res.data).toHaveLength(1);
  });

  it("reads a facility public profile by id", async () => {
    publicClient.get.mockResolvedValue({ data: { data: { id: 5, name: "Parirenyatwa", level: "TERTIARY" } } });
    const profile = await fetchPublicFacilityProfile(5);
    expect(publicClient.get).toHaveBeenCalledWith("/internal/v1/public/gateway/facilities/5/profile");
    expect(profile?.level).toBe("TERTIARY");
  });
});
