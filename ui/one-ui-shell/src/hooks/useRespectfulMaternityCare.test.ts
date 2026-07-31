import { renderHook, waitFor, act } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useRespectfulMaternityCare } from "./useRespectfulMaternityCare";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

const INSTRUMENT_PAYLOAD = {
  instrument_id: "RESPECTFUL_MATERNITY_CARE",
  name: "Respectful maternity care experience",
  applies_to: "Any woman who received intrapartum or immediate postnatal care at a facility.",
  rating_domain: "CLIENT_EXPERIENCE",
  approval_status: "ENGINEERING_SEED",
  scale: {
    type: "LIKERT_5",
    low: 1,
    high: 5,
    low_means: "never",
    high_means: "always",
    note: "A blank is NOT_ASKED and must never be scored as neutral.",
  },
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
  reporting_rules: ["Domain scores are stored SEPARATELY."],
  anonymous_by_default: true,
  regulation_firewall: "A rating never mutates a licence, scope, employment or access.",
};

describe("useRespectfulMaternityCare", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("fetches and normalizes the instrument from the BFF, never inventing questions locally", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD, meta: { request_id: "r-1" } });

    const { result } = renderHook(() => useRespectfulMaternityCare());

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(get).toHaveBeenCalledWith("/internal/v1/maternity/respectful-care/instrument");
    expect(result.current.instrumentUnavailable).toBe(false);
    expect(result.current.instrument?.measures).toHaveLength(2);
    expect(result.current.instrument?.measures[0]).toEqual(
      expect.objectContaining({
        measure: "respect_dignity",
        prompt: "Were you treated with respect and dignity?",
        reverseScored: false,
      }),
    );
    expect(result.current.instrument?.measures[1]).toEqual(
      expect.objectContaining({ measure: "never_left_alone", reverseScored: true }),
    );
    expect(result.current.instrument?.scale).toEqual(
      expect.objectContaining({ low: 1, high: 5, lowMeans: "never", highMeans: "always" }),
    );
  });

  it("marks the instrument unavailable on a 502 and does not fabricate a fallback measure set", async () => {
    get.mockRejectedValue({
      status: 502,
      error: "instrument_unavailable",
      message: "The measure set could not be retrieved.",
    });

    const { result } = renderHook(() => useRespectfulMaternityCare());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.instrumentUnavailable).toBe(true);
    expect(result.current.instrument).toBeNull();
  });

  it("also treats a malformed/empty instrument payload as unavailable rather than rendering zero questions silently", async () => {
    get.mockResolvedValue({ data: { instrument_id: "X", measures: [] } });

    const { result } = renderHook(() => useRespectfulMaternityCare());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.instrumentUnavailable).toBe(true);
    expect(result.current.instrument).toBeNull();
  });

  it("submits raw answers unchanged and surfaces the claim code from the narrative result", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD });
    post.mockResolvedValue({
      data: {
        narrative: {
          filed: true,
          case_reference: "RITO-2026-000123",
          claim_code: "CLAIM-ABC-123",
          routed_as: "COMPLIMENT",
        },
        scores: { stored: false, reason: "no_attribution", message: "no attribution available" },
        anonymous: true,
        narrative_linked_to_rating: false,
        linkage_note: "kept separate",
      },
    });

    const { result } = renderHook(() => useRespectfulMaternityCare());
    await waitFor(() => expect(result.current.loading).toBe(false));

    let submitResult;
    await act(async () => {
      submitResult = await result.current.submit({
        answers: { respect_dignity: 5, never_left_alone: 2 },
        narrative: "It went well",
      });
    });

    expect(post).toHaveBeenCalledWith(
      "/internal/v1/maternity/respectful-care/feedback",
      expect.objectContaining({ answers: { respect_dignity: 5, never_left_alone: 2 } }),
    );
    expect(submitResult).toEqual(
      expect.objectContaining({
        anonymous: true,
        narrative: expect.objectContaining({ filed: true, claimCode: "CLAIM-ABC-123" }),
        scores: expect.objectContaining({ stored: false, reason: "no_attribution" }),
      }),
    );
    expect(result.current.result?.narrative?.claimCode).toBe("CLAIM-ABC-123");
  });

  it("surfaces a submission error without discarding the loaded instrument", async () => {
    get.mockResolvedValue({ data: INSTRUMENT_PAYLOAD });
    post.mockRejectedValue({ status: 500, message: "downstream unavailable" });

    const { result } = renderHook(() => useRespectfulMaternityCare());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.submit({ narrative: "Something happened" });
    });

    expect(result.current.submitError).toContain("downstream unavailable");
    expect(result.current.instrument).not.toBeNull();
  });
});
