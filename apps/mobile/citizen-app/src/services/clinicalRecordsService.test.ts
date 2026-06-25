/**
 * Clinical Records Service tests (G054) — citizen immunizations / care plans / referrals.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import { fetchImmunizations, fetchCarePlans, fetchReferrals } from "./clinicalRecordsService";

describe("clinicalRecordsService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("fetches immunizations from the citizen BFF endpoint", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: [{ id: "i1", vaccineName: "BCG", status: "COMPLETED" }] },
    });
    const res = await fetchImmunizations();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/immunizations");
    expect(res.items).toHaveLength(1);
    expect(res.items[0].vaccineName).toBe("BCG");
  });

  it("fetches care plans (no pagination params)", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "cp1", status: "ACTIVE" }] } });
    const res = await fetchCarePlans();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/care-plans");
    expect(res.items).toHaveLength(1);
  });

  it("fetches referrals with pagination", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "r1", status: "PENDING" }] } });
    const res = await fetchReferrals({ page: 1, size: 10 });
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/referrals?page=1&size=10");
    expect(res.items[0].status).toBe("PENDING");
  });

  it("unwraps a paged {content:[...]} envelope", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: { content: [{ id: "x" }] } } });
    expect((await fetchImmunizations()).items).toHaveLength(1);
  });

  it("unwraps a bare array payload", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "a" }, { id: "b" }] } });
    expect((await fetchReferrals()).items).toHaveLength(2);
  });

  it("returns empty items for an unrecognised/empty payload", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: null } });
    expect((await fetchCarePlans()).items).toEqual([]);
  });
});
