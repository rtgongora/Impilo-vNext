/**
 * Fundraisers (crowdfunding) service tests — governed BFF endpoints, idempotent donate,
 * honest error propagation (no optimistic success).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));
vi.mock("@impilo/mobile-trust", () => ({ generateId: () => "11111111-2222-4333-8444-555555555555" }));

import { fetchCampaigns, fetchMine, fetchDetail, donate } from "./crowdfundingService";

const CASE = {
  id: "case-1",
  assistanceReference: "AS-2026-0001",
  title: "Help with surgery",
  story: "I need an operation.",
  category: "SURGERY",
  targetAmount: "1000.00",
  raisedAmount: 250,
  donorCount: 3,
  currency: "USD",
  verificationStatus: "ACTIVE",
  attestationPublicToken: "https://verify.example/att-1",
};

describe("crowdfundingService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists public fundraisers from the BFF and coerces amounts", async () => {
    mockApiClient.get.mockResolvedValue({ data: [CASE] });
    const campaigns = await fetchCampaigns();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/citizen/fundraisers");
    expect(campaigns).toHaveLength(1);
    expect(campaigns[0].targetAmount).toBe(1000);
    expect(campaigns[0].raisedAmount).toBe(250);
    expect(campaigns[0].verificationStatus).toBe("ACTIVE");
    expect(campaigns[0].attestationPublicToken).toBe("https://verify.example/att-1");
  });

  it("lists my fundraisers (any status) from /mine", async () => {
    mockApiClient.get.mockResolvedValue({ data: [{ ...CASE, verificationStatus: "DRAFT" }] });
    const mine = await fetchMine();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/citizen/fundraisers/mine");
    expect(mine[0].verificationStatus).toBe("DRAFT");
  });

  it("reads the composed detail (case + share/verification pointers)", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        case: CASE,
        shareUrl: "/share/fund/tok-1",
        independentVerificationUrl: "https://verify.example/att-1",
        moneyUnavailable: true,
      },
    });
    const detail = await fetchDetail("case-1");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/citizen/fundraisers/case-1");
    expect(detail.campaign.title).toBe("Help with surgery");
    expect(detail.shareUrl).toBe("/share/fund/tok-1");
    expect(detail.independentVerificationUrl).toBe("https://verify.example/att-1");
    expect(detail.moneyUnavailable).toBe(true);
  });

  it("donates via the governed endpoint with an Idempotency-Key per attempt", async () => {
    mockApiClient.post.mockResolvedValue({ data: { contributionId: "ct-1" } });
    const r = await donate("case-1", { amount: 25, message: "Get well", isAnonymous: true });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/citizen/fundraisers/case-1/donate",
      { amount: 25, message: "Get well", isAnonymous: true },
      { headers: { "Idempotency-Key": "11111111-2222-4333-8444-555555555555" } },
    );
    expect(r.contributionId).toBe("ct-1");
  });

  it("propagates donate rejections (409 not-open / insufficient funds) — no fake success", async () => {
    mockApiClient.post.mockRejectedValue(
      Object.assign(new Error("This fundraiser is not open for donations (CANCELLED)."), {
        status: 409,
        code: "FUNDRAISER_NOT_OPEN",
      }),
    );
    await expect(donate("case-1", { amount: 25 })).rejects.toThrow(
      "This fundraiser is not open for donations (CANCELLED).",
    );
  });
});
