import { describe, expect, it, vi, beforeEach } from "vitest";

const apiClientMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: apiClientMock }));

import {
  fetchInstrument,
  submitRespectfulCareFeedback,
  RespectfulCareUnavailableError,
} from "./respectfulCareService";

const INSTRUMENT_PAYLOAD = {
  instrument_id: "RESPECTFUL_MATERNITY_CARE",
  name: "Respectful maternity care experience",
  applies_to: "Any woman who received intrapartum or immediate postnatal care at a facility.",
  scale: { type: "LIKERT_5", low: 1, high: 5, low_means: "never", high_means: "always" },
  measures: [
    {
      measure: "respect_dignity",
      prompt: "Were you treated with respect and dignity?",
      mistreatment_category: "NON_DIGNIFIED_CARE",
      reverse_scored: false,
    },
    {
      measure: "never_left_alone",
      prompt: "Were you left alone when you needed someone?",
      mistreatment_category: "ABANDONMENT",
      reverse_scored: true,
    },
  ],
  anonymous_by_default: true,
};

describe("respectfulCareService", () => {
  beforeEach(() => {
    apiClientMock.get.mockReset();
    apiClientMock.post.mockReset();
  });

  it("fetches and normalizes the instrument (BFF returns { data: { data, meta } }, no ApiEnvelope auto-unwrap)", async () => {
    apiClientMock.get.mockResolvedValue({
      data: { data: INSTRUMENT_PAYLOAD, meta: { request_id: "r-1" } },
      status: 200,
      correlationId: "c-1",
      headers: {},
    });

    const instrument = await fetchInstrument();

    expect(apiClientMock.get).toHaveBeenCalledWith("/internal/v1/maternity/respectful-care/instrument");
    expect(instrument.measures).toHaveLength(2);
    expect(instrument.measures[0]).toMatchObject({
      measure: "respect_dignity",
      prompt: "Were you treated with respect and dignity?",
      reverseScored: false,
    });
    expect(instrument.measures[1]).toMatchObject({ measure: "never_left_alone", reverseScored: true });
    expect(instrument.scale).toMatchObject({ low: 1, high: 5, lowMeans: "never", highMeans: "always" });
  });

  it("throws RespectfulCareUnavailableError rather than inventing a fallback instrument", async () => {
    apiClientMock.get.mockResolvedValue({
      data: { data: { instrument_id: "X", measures: [] } },
      status: 200,
      correlationId: "c-2",
      headers: {},
    });

    await expect(fetchInstrument()).rejects.toBeInstanceOf(RespectfulCareUnavailableError);
  });

  it("propagates a fetch rejection (e.g. 502 from the BFF) rather than swallowing it into a fallback", async () => {
    apiClientMock.get.mockRejectedValue(new Error("HTTP 502"));
    await expect(fetchInstrument()).rejects.toThrow("HTTP 502");
  });

  it("submits raw answers unchanged and returns the claim code from the narrative result", async () => {
    apiClientMock.post.mockResolvedValue({
      data: {
        data: {
          narrative: {
            filed: true,
            case_reference: "RITO-2026-000123",
            claim_code: "CLAIM-ABC-123",
            routed_as: "COMPLIMENT",
          },
          scores: { stored: false, reason: "no_attribution" },
          anonymous: true,
          narrative_linked_to_rating: false,
          linkage_note: "kept separate",
        },
        meta: { request_id: "r-2" },
      },
      status: 200,
      correlationId: "c-3",
      headers: {},
    });

    const result = await submitRespectfulCareFeedback({
      answers: { respect_dignity: 5, never_left_alone: 2 },
      narrative: "It went well",
      identifyMe: false,
    });

    expect(apiClientMock.post).toHaveBeenCalledWith(
      "/internal/v1/maternity/respectful-care/feedback",
      expect.objectContaining({ answers: { respect_dignity: 5, never_left_alone: 2 } }),
    );
    expect(result.narrative).toMatchObject({ filed: true, claimCode: "CLAIM-ABC-123" });
    expect(result.scores).toMatchObject({ stored: false, reason: "no_attribution" });
    expect(result.anonymous).toBe(true);
    expect(result.narrativeLinkedToRating).toBe(false);
  });
});
