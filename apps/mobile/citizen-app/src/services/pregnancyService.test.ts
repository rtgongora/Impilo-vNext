/**
 * Pregnancy episode booking service tests — 201/200 both treated as success, 409 conflict and 422
 * undatable surfaced as typed domain outcomes (never a generic failure), and the "me" sentinel
 * used for reads so no raw CPID is ever sent from the app (RMNP W12, Identity Contract §12).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const { MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    status: number;
    code: string;
    details?: Record<string, unknown>;
    constructor(status: number, code: string, message = "api error", details?: Record<string, unknown>) {
      super(message);
      this.status = status;
      this.code = code;
      this.details = details;
    }
  }
  return { MockApiError };
});
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient, ApiError: MockApiError }));
vi.mock("@impilo/mobile-trust", () => ({ generateId: () => "11111111-2222-4333-8444-555555555555" }));

import {
  bookPregnancy,
  fetchCurrentPregnancy,
  fetchPregnancyHistory,
  fetchMyReproductiveIntention,
  recordMyReproductiveIntention,
  newClientOfflineId,
  isPregnancyConflict,
  isPregnancyUndatable,
  PregnancyConflictError,
  PregnancyUndatableError,
} from "./pregnancyService";

describe("pregnancyService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("generates a stable offline-attempt id", () => {
    expect(newClientOfflineId()).toBe("11111111-2222-4333-8444-555555555555");
  });

  it("books a pregnancy from an LMP dating basis and normalizes the created (201) episode", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          pregnancy_episode_id: "preg-1",
          subject_cpid: "cpid-1",
          status: "ONGOING",
          pregnancy_start_date: "2026-01-01",
          estimated_delivery_date: "2026-10-08",
          dating_method: "LMP",
          dating_confidence: "MODERATE",
          risk_status: "NOT_ASSESSED",
          replayed: false,
        },
      },
    });

    const episode = await bookPregnancy({
      datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-01-01", lastMenstrualPeriodCertain: true }],
      clientOfflineId: "attempt-1",
    });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/confidential/maternity/pregnancy-episodes",
      expect.objectContaining({ clientOfflineId: "attempt-1" }),
    );
    expect(episode.pregnancyEpisodeId).toBe("preg-1");
    expect(episode.replayed).toBe(false);
    // NOT_ASSESSED must render exactly as recorded — never coerced into a "low risk" reading.
    expect(episode.riskStatus).toBe("NOT_ASSESSED");
  });

  it("treats a replayed (200) offline packet as success, not a duplicate", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { pregnancy_episode_id: "preg-1", status: "ONGOING", replayed: true } },
    });
    const episode = await bookPregnancy({
      datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-01-01" }],
      clientOfflineId: "attempt-1",
    });
    expect(episode.replayed).toBe(true);
    expect(episode.pregnancyEpisodeId).toBe("preg-1");
  });

  it("rejects with PregnancyConflictError on 409, carrying the existing episode id and task", async () => {
    mockApiClient.post.mockRejectedValue(
      new MockApiError(409, "PREGNANCY_ALREADY_BOOKED", "This person already has an ongoing pregnancy episode.", {
        existing_pregnancy_episode_id: "preg-existing",
        reconciliation_task: "Open the existing pregnancy episode and reconcile this booking against it.",
      }),
    );

    const err = await bookPregnancy({ datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-01-01" }] }).catch(
      (e) => e,
    );
    expect(isPregnancyConflict(err)).toBe(true);
    expect(err).toBeInstanceOf(PregnancyConflictError);
    expect((err as PregnancyConflictError).existingPregnancyEpisodeId).toBe("preg-existing");
    expect((err as PregnancyConflictError).reconciliationTask).toContain("Open the existing pregnancy episode");
  });

  it("rejects with PregnancyUndatableError on 422 — never treated the same as a 500", async () => {
    mockApiClient.post.mockRejectedValue(
      new MockApiError(422, "PREGNANCY_UNDATABLE", "No usable dating basis was supplied."),
    );
    const err = await bookPregnancy({ datingBases: [] }).catch((e) => e);
    expect(isPregnancyUndatable(err)).toBe(true);
    expect(err).toBeInstanceOf(PregnancyUndatableError);
    expect((err as Error).message).toContain("No usable dating basis");
  });

  it("propagates a 500 unchanged — never reinterpreted as a 409 conflict", async () => {
    mockApiClient.post.mockRejectedValue(new MockApiError(500, "INTERNAL_ERROR"));
    const err = await bookPregnancy({ datingBases: [{ method: "LMP", lastMenstrualPeriod: "2026-01-01" }] }).catch(
      (e) => e,
    );
    expect(isPregnancyConflict(err)).toBe(false);
    expect(isPregnancyUndatable(err)).toBe(false);
  });

  it("fetches her current pregnancy via the 'me' sentinel — no raw CPID sent", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { pregnancy_episode_id: "preg-1", status: "ONGOING" } },
    });
    const episode = await fetchCurrentPregnancy();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/confidential/maternity/pregnancy-episodes/me/current");
    expect(episode?.pregnancyEpisodeId).toBe("preg-1");
  });

  it("returns null for an absent current pregnancy — never a thrown 404", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: null } });
    expect(await fetchCurrentPregnancy()).toBeNull();
  });

  it("fetches her pregnancy history via the 'me' sentinel", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: [{ pregnancy_episode_id: "preg-0", status: "DELIVERED", ended_on: "2024-05-01" }] },
    });
    const history = await fetchPregnancyHistory();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/confidential/maternity/pregnancy-episodes/me");
    expect(history).toHaveLength(1);
    expect(history[0].status).toBe("DELIVERED");
  });

  it("fetches her reproductive intention via the 'me' sentinel — no raw CPID sent", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { intention_id: "int-1", intention: "WANTS_PREGNANCY_LATER", timeframe_months: 12 } },
    });
    const intention = await fetchMyReproductiveIntention();
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/reproductive-intentions/me/current",
    );
    expect(intention?.intention).toBe("WANTS_PREGNANCY_LATER");
    expect(intention?.timeframeMonths).toBe(12);
  });

  it("returns null for an absent intention — withhold and absence are indistinguishable", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: null } });
    expect(await fetchMyReproductiveIntention()).toBeNull();
  });

  it("records her intention with the 'me' sentinel as subject", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { intention_id: "int-2", intention: "UNDECIDED" } },
    });
    const saved = await recordMyReproductiveIntention("UNDECIDED");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/reproductive-intentions",
      { subjectCpid: "me", intention: "UNDECIDED", recordedBy: "citizen-self" },
    );
    expect(saved.intention).toBe("UNDECIDED");
  });

  it("propagates PCT_UNAVAILABLE on the intention read — never rendered as 'none recorded'", async () => {
    mockApiClient.get.mockRejectedValue(new MockApiError(502, "PCT_UNAVAILABLE"));
    await expect(fetchMyReproductiveIntention()).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });
});
