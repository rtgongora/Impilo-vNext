/**
 * MPDSR review service tests — the BFF forwards raw Rito payloads (no ApiEnvelope), but the list
 * normalizer must also tolerate an envelope-shaped `{ data: [] }` body so a future BFF wrapping
 * change cannot silently render "no reviews".
 */
import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { fetchMpdsrReviews, fetchMpdsrReview } from "./mpdsrService";

vi.mock("@impilo/mobile-api-client", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    apiClient: { get: vi.fn(), post: vi.fn() },
  };
});

beforeEach(() => {
  vi.mocked(apiClient.get).mockReset();
});

describe("mpdsrService", () => {
  it("lists reviews from a raw array body", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: "rev-1", status: "OPEN", reviewLevel: "FACILITY" }],
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const reviews = await fetchMpdsrReviews();
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/rito/mpdsr/reviews");
    expect(reviews).toHaveLength(1);
    expect(reviews[0].id).toBe("rev-1");
  });

  it("lists reviews from an envelope-shaped body", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "rev-2", status: "CLOSED" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const reviews = await fetchMpdsrReviews();
    expect(reviews).toHaveLength(1);
    expect(reviews[0].id).toBe("rev-2");
  });

  it("passes the status filter through as a query parameter", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [], status: 200, correlationId: "c1", headers: {} });
    await fetchMpdsrReviews("OPEN");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/rito/mpdsr/reviews?status=OPEN");
  });

  it("fetches review detail with committee sections", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        review: { id: "rev-3", status: "OPEN" },
        findings: [{ id: "f-1", delayType: "TYPE1_SEEKING" }],
        actions: [],
        readinessTasks: [{ id: "t-1", taskReference: "RT-1", status: "REQUESTED" }],
      },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const detail = await fetchMpdsrReview("rev-3");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/rito/mpdsr/reviews/rev-3");
    expect(detail.review?.id).toBe("rev-3");
    expect(detail.findings?.[0].delayType).toBe("TYPE1_SEEKING");
    expect(detail.readinessTasks?.[0].taskReference).toBe("RT-1");
  });
});
