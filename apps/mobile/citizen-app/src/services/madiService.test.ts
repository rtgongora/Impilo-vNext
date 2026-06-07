import { describe, expect, it, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}));

import {
  registerDonor,
  fetchDonorProfile,
  fetchDrivesNearMe,
  submitDonorFeedback,
  fetchDonorHistory,
  fetchNextEligibility,
} from "./madiService";

const BASE = "/internal/v1/mobile/citizen/madi";

describe("citizen madiService", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockPut.mockReset();
  });

  it("registerDonor posts to register endpoint", async () => {
    mockPost.mockResolvedValue({
      data: { data: { donor_id: "donor-1", blood_group: "O", rh_factor: "POSITIVE" } },
    });

    const profile = await registerDonor({ blood_group: "O", rh_factor: "POSITIVE" });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/register`, {
      blood_group: "O",
      rh_factor: "POSITIVE",
    });
    expect(profile?.donor_id).toBe("donor-1");
  });

  it("fetchDonorProfile reads profile endpoint", async () => {
    mockGet.mockResolvedValue({
      data: { data: { donor_id: "donor-2", status: "ACTIVE" } },
    });

    const profile = await fetchDonorProfile();

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/profile`);
    expect(profile?.status).toBe("ACTIVE");
  });

  it("fetchDrivesNearMe builds query params", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ drive_id: "drive-1", title: "City Hall Drive" }] },
    });

    const drives = await fetchDrivesNearMe({ ndila_site_ref: "site-abc", radius_km: 10 });

    expect(mockGet).toHaveBeenCalledWith(
      `${BASE}/drives/near-me?ndila_site_ref=site-abc&radius_km=10`,
    );
    expect(drives).toHaveLength(1);
  });

  it("submitDonorFeedback posts feedback payload", async () => {
    mockPost.mockResolvedValue({ data: {} });

    const ok = await submitDonorFeedback({ rating: 5, comments: "Great experience" });

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/feedback`, {
      rating: 5,
      comments: "Great experience",
    });
    expect(ok).toBe(true);
  });

  it("fetchDonorHistory returns empty object on failure", async () => {
    mockGet.mockRejectedValue(new Error("network"));

    const history = await fetchDonorHistory();

    expect(history).toEqual({});
  });

  it("fetchNextEligibility reads eligibility endpoint", async () => {
    mockGet.mockResolvedValue({
      data: { data: { eligible: false, next_eligible_date: "2026-09-01" } },
    });

    const eligibility = await fetchNextEligibility();

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/next-eligibility`);
    expect(eligibility?.next_eligible_date).toBe("2026-09-01");
  });
});
