import { describe, expect, it, vi } from "vitest";

const mockGet = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

import { getProviderLearningSummary, summarizeProviderLearning } from "./learningService";

describe("summarizeProviderLearning", () => {
  it("maps array payloads into dashboard KPI counts", () => {
    const summary = summarizeProviderLearning({
      inProgress: [{ id: "ip-1" }],
      required: [{ id: "req-1" }, { id: "req-2" }],
      overdue: [{ id: "od-1" }],
      cpdEligibleCompletions: [{ id: "cpd-1" }],
    });

    expect(summary).toEqual({
      inProgress: 1,
      required: 2,
      overdue: 1,
      cpdEligible: 1,
    });
  });

  it("maps numeric aggregate payloads when arrays are absent", () => {
    const summary = summarizeProviderLearning({
      inProgressCount: "3",
      requiredCount: 4,
      overdueCount: "2",
      cpdEligibleCount: 6,
    });

    expect(summary).toEqual({
      inProgress: 3,
      required: 4,
      overdue: 2,
      cpdEligible: 6,
    });
  });
});

describe("getProviderLearningSummary", () => {
  it("calls canonical my-learning endpoint and returns summarized counters", async () => {
    mockGet.mockResolvedValue({
      data: {
        data: {
          required: [{ id: "req-1" }],
          overdue: [{ id: "od-1" }, { id: "od-2" }],
          cpdEligibleCompletions: [],
          inProgress: [{ id: "ip-1" }, { id: "ip-2" }],
        },
      },
    });

    const result = await getProviderLearningSummary({
      subjectType: "PROVIDER",
      subjectId: "prov-123",
    });

    expect(mockGet).toHaveBeenCalledWith(
      "/internal/v1/learning/v11/my-learning?subjectType=PROVIDER&subjectId=prov-123",
    );
    expect(result).toEqual({
      inProgress: 2,
      required: 1,
      overdue: 2,
      cpdEligible: 0,
    });
  });
});
