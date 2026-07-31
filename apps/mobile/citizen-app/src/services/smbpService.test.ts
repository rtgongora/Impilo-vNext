/**
 * SMBP verdict service tests — happy path, dangerSignPresent omission, and the 502
 * READINGS_UNAVAILABLE/CKP_UNAVAILABLE outage codes rendering as an honest outage rather than a
 * clinical verdict (RMNP W12, docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md §4).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const { MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    status: number;
    code: string;
    constructor(status: number, code: string, message = "api error") {
      super(message);
      this.status = status;
      this.code = code;
    }
  }
  return { MockApiError };
});
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient, ApiError: MockApiError }));

import {
  fetchSmbpVerdict,
  fetchMyMonitoringPlans,
  isSmbpUnavailableError,
  SmbpUnavailableError,
} from "./smbpService";

describe("smbpService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("fetches the verdict for a plan without dangerSignPresent when not asked", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          verdict: "CONTROLLED",
          message: "Your readings look controlled.",
          note: "Keep taking readings as advised.",
          summary: { readingCount: 6, meanCount: 6, sufficient: true, anySevere: false, elevatedMean: false },
          alerts: [],
        },
        meta: { plan_id: "plan-1", raw_reading_count: 12, paired_reading_count: 6 },
      },
    });

    const result = await fetchSmbpVerdict("plan-1");

    const url = mockApiClient.get.mock.calls[0][0] as string;
    expect(url).toContain("planId=plan-1");
    expect(url).not.toContain("dangerSignPresent");
    expect(result.verdict).toBe("CONTROLLED");
    expect(result.rawReadingCount).toBe(12);
    expect(result.pairedReadingCount).toBe(6);
    expect(result.planId).toBe("plan-1");
  });

  it("sends dangerSignPresent when explicitly asked (never coerced when omitted)", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { verdict: "URGENT", message: "m", note: "n", summary: {}, alerts: [] }, meta: {} },
    });
    await fetchSmbpVerdict("plan-2", true);
    const url = mockApiClient.get.mock.calls[0][0] as string;
    expect(url).toContain("dangerSignPresent=true");
  });

  it("never reports INSUFFICIENT_DATA as CONTROLLED — too few readings stays INSUFFICIENT_DATA", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          verdict: "INSUFFICIENT_DATA",
          message: "Not enough readings yet.",
          note: "",
          summary: { readingCount: 1, meanCount: 0, sufficient: false, anySevere: false, elevatedMean: false },
          alerts: [],
        },
        meta: { plan_id: "plan-3", raw_reading_count: 1, paired_reading_count: 0 },
      },
    });
    const result = await fetchSmbpVerdict("plan-3");
    expect(result.verdict).toBe("INSUFFICIENT_DATA");
    expect(result.summary.sufficient).toBe(false);
  });

  it("throws SmbpUnavailableError for a 502 READINGS_UNAVAILABLE — never rendered as a verdict", async () => {
    mockApiClient.get.mockRejectedValue(new MockApiError(502, "READINGS_UNAVAILABLE", "readings could not be read"));
    await expect(fetchSmbpVerdict("plan-4")).rejects.toBeInstanceOf(SmbpUnavailableError);
    try {
      await fetchSmbpVerdict("plan-4");
    } catch (err) {
      expect(isSmbpUnavailableError(err)).toBe(true);
    }
  });

  it("throws SmbpUnavailableError for a 502 CKP_UNAVAILABLE", async () => {
    mockApiClient.get.mockRejectedValue(
      new MockApiError(502, "CKP_UNAVAILABLE", "the series verdict could not be computed"),
    );
    const err = await fetchSmbpVerdict("plan-5").catch((e) => e);
    expect(isSmbpUnavailableError(err)).toBe(true);
  });

  it("propagates any other rejection unchanged (not mistaken for an SMBP outage)", async () => {
    mockApiClient.get.mockRejectedValue(new MockApiError(500, "INTERNAL"));
    const err = await fetchSmbpVerdict("plan-6").catch((e) => e);
    expect(isSmbpUnavailableError(err)).toBe(false);
  });

  it("lists monitoring plans, normalizing the raw upstream array", async () => {
    mockApiClient.get.mockResolvedValue({
      data: [
        { id: "plan-1", programmeCode: "HYPERTENSION", status: "ACTIVE" },
        { id: "plan-2", programmeCode: "DIABETES", status: "ACTIVE" },
      ],
    });
    const plans = await fetchMyMonitoringPlans();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/telemonitoring/my/plans");
    expect(plans).toHaveLength(2);
    expect(plans[0].programmeCode).toBe("HYPERTENSION");
  });
});
