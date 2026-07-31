import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient, ApiError } from "@impilo/mobile-api-client";
import {
  openPartograph,
  getActivePartograph,
  getPartograph,
  addPartographPoint,
  closePartograph,
  openCtgSession,
  getActiveCtgSession,
  addCtgAnnotation,
  computeNearMissIndicators,
  fetchBirthDestination,
  getMaternitySummary,
  fetchContraceptionCoverage,
  fetchContraceptionHistory,
  fetchPregnancyLosses,
  fetchTopAuthorisations,
  fetchPatientPregnancyEpisodes,
  fetchCurrentReproductiveIntention,
  fetchDeliveryRecordsForMother,
  recordReproductiveIntention,
  openPreconceptionPlan,
  startFertilityEpisode,
  recordDeliveryRecord,
} from "./maternityService";

vi.mock("@impilo/mobile-api-client", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
  };
});

function ok<T>(data: T) {
  return { data: { data }, status: 200, correlationId: "c1", headers: {} };
}

beforeEach(() => {
  vi.mocked(apiClient.get).mockReset();
  vi.mocked(apiClient.post).mockReset();
  vi.mocked(apiClient.patch).mockReset();
});

describe("partograph — the 200-vs-502 distinction (contract §4.1, §4.2)", () => {
  it("treats partograph_active: false as a real answer, not an error", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", partograph_active: false }));
    const result = await getActivePartograph("P1");
    expect(result).toEqual({ partographActive: false, patientId: "P1" });
  });

  it("returns the session (with its embedded progress) when one is open", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ session_id: "S1", patient_id: "P1", status: "ACTIVE", progress: { status: "LEFT_OF_ALERT" } }),
    );
    const result = await getActivePartograph("P1");
    expect(result.partographActive).toBe(true);
    if (result.partographActive) {
      expect(result.session.session_id).toBe("S1");
      expect(result.session.progress.status).toBe("LEFT_OF_ALERT");
    }
  });

  it("propagates a PCT_UNAVAILABLE failure rather than collapsing it into 'no session'", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(getActivePartograph("P1")).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });

  it("builds the active-session query with patientId and optional encounterId", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", partograph_active: false }));
    await getActivePartograph("P1", "E1");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/active?patientId=P1&encounterId=E1",
    );
  });
});

describe("partograph — session lifecycle and observation writes", () => {
  it("openPartograph posts camelCase identifiers", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ session_id: "S1", patient_id: "P1", status: "ACTIVE" }));
    await openPartograph({ patientId: "P1", encounterId: "E1" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/maternity/partograph/sessions", {
      patientId: "P1",
      journeyId: undefined,
      encounterId: "E1",
      labourPhase: undefined,
    });
  });

  it("getPartograph unwraps the session, its points and progress", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ session_id: "S1", points: [{ observation_id: "O1" }], progress: { status: "INSUFFICIENT_DATA" } }),
    );
    const detail = await getPartograph("S1");
    expect(detail.points).toHaveLength(1);
    expect(detail.progress.status).toBe("INSUFFICIENT_DATA");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/maternity/partograph/sessions/S1");
  });

  it("addPartographPoint sends exactly the answers it is given — nothing added, nothing carried forward", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ point: { observation_id: "O1" }, progress: { status: "LEFT_OF_ALERT" } }),
    );
    const answers = { observedAt: "2026-07-26T10:00:00Z", phase: "ACTIVE_LABOUR", cervicalDilationCm: 5 };
    const result = await addPartographPoint("S1", answers);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/S1/points",
      answers,
    );
    expect(result.progress.status).toBe("LEFT_OF_ALERT");
  });

  it("addPartographPoint returns the assessment alongside the point (contract §4.3)", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ point: { observation_id: "O2" }, progress: { status: "AT_OR_RIGHT_OF_ACTION" } }),
    );
    const result = await addPartographPoint("S1", { observedAt: "x", phase: "ACTIVE_LABOUR" });
    expect(result.point.observation_id).toBe("O2");
    expect(result.progress.status).toBe("AT_OR_RIGHT_OF_ACTION");
  });

  it("closePartograph uses PATCH, accepting the verb the shell has always sent", async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(ok({ session_id: "S1", status: "CLOSED" }));
    await closePartograph("S1", { outcome: "DELIVERED" });
    expect(apiClient.patch).toHaveBeenCalledWith(
      "/internal/v1/maternity/partograph/sessions/S1/close",
      { outcome: "DELIVERED" },
    );
  });
});

describe("CTG — the same 200-vs-502 distinction applies", () => {
  it("treats ctg_active: false as a real answer", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ patient_id: "P1", ctg_active: false }));
    const result = await getActiveCtgSession("P1");
    expect(result).toEqual({ ctgActive: false, patientId: "P1" });
  });

  it("propagates a PCT_UNAVAILABLE failure rather than reporting an empty trace", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(getActiveCtgSession("P1")).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });

  it("openCtgSession posts camelCase identifiers", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(ok({ session_id: "S2", patient_id: "P1", status: "ACTIVE" }));
    await openCtgSession({ patientId: "P1" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/maternity/ctg/sessions", {
      patientId: "P1",
      journeyId: undefined,
      encounterId: undefined,
      monitoringMode: undefined,
      deviceId: undefined,
    });
  });

  it("addCtgAnnotation never derives severity — it sends exactly what the clinician chose", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ annotation_id: "A1", category: "LATE_DECELERATION", severity: "ABNORMAL" }),
    );
    const answers = { recordedAt: "2026-07-26T10:00:00Z", channel: "FHR", category: "LATE_DECELERATION", severity: "ABNORMAL" };
    const annotation = await addCtgAnnotation("S2", answers);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/maternity/ctg/sessions/S2/annotations",
      answers,
    );
    expect(annotation.severity).toBe("ABNORMAL");
  });
});

describe("computeNearMissIndicators — the null-ratio-is-not-zero contract", () => {
  it("posts indicatorPeriod and cases exactly as given", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({
        indicator_period: "2026-Q2",
        near_miss_count: 2,
        maternal_death_count: 1,
        indeterminate_count: 0,
        severe_maternal_outcome_count: 3,
        near_miss_to_death_ratio: 2,
        near_miss_to_death_ratio_upper_bound: null,
        mortality_index: 33.33,
        mortality_index_lower_bound: null,
        mortality_index_direction: "HIGHER_IS_WORSE",
        note: null,
      }),
    );
    const cases = [{ outcome: "NEAR_MISS" as const }, { outcome: "MATERNAL_DEATH" as const }];
    const result = await computeNearMissIndicators("2026-Q2", cases);

    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/clinical/maternal/near-miss/indicators", {
      indicatorPeriod: "2026-Q2",
      cases,
    });
    expect(result.near_miss_to_death_ratio).toBe(2);
  });

  it("resolves a null ratio as null, never coerced to 0", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({
        indicator_period: "2026-Q3",
        near_miss_count: 1,
        maternal_death_count: 0,
        indeterminate_count: 2,
        severe_maternal_outcome_count: 1,
        near_miss_to_death_ratio: null,
        near_miss_to_death_ratio_upper_bound: 1,
        mortality_index: null,
        mortality_index_lower_bound: 0,
        mortality_index_direction: "HIGHER_IS_WORSE",
        note: "No deaths recorded.",
      }),
    );
    const result = await computeNearMissIndicators("2026-Q3", [{ outcome: "NEAR_MISS" }]);
    expect(result.near_miss_to_death_ratio).toBeNull();
    expect(result.mortality_index).toBeNull();
    // Never dropped even though it feeds neither ratio.
    expect(result.indeterminate_count).toBe(2);
  });

  it("propagates a 502 refusal rather than a fabricated computation", async () => {
    vi.mocked(apiClient.post).mockRejectedValue(
      new ApiError({ code: "near_miss_unavailable", message: "down", status: 502, correlationId: "c1" }),
    );
    await expect(computeNearMissIndicators("p", [])).rejects.toMatchObject({ code: "near_miss_unavailable" });
  });
});

describe("fetchBirthDestination — the three honesty rules (BirthDestinationService.java)", () => {
  it("requests with facilityId and requiredLevel as query params", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({
        facility_id: 42,
        required_level: "BEMONC",
        status: "MEETS_REQUIRED_LEVEL",
        operational_gate_passed: true,
        emonc_verdict: "BEMONC",
        call_ahead: true,
        message: "Meets the level of care needed.",
        guidance: "Confirm by phone.",
      }),
    );
    const verdict = await fetchBirthDestination(42, "BEMONC");

    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/maternity/birth-destination?facilityId=42&requiredLevel=BEMONC",
    );
    expect(verdict.status).toBe("MEETS_REQUIRED_LEVEL");
  });

  it("defaults requiredLevel to UNKNOWN when not supplied", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ facility_id: 7, required_level: "UNKNOWN", status: "REQUIRED_LEVEL_UNKNOWN", operational_gate_passed: true, emonc_verdict: null, call_ahead: true, message: "m", guidance: "g" }),
    );
    await fetchBirthDestination(7);
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/maternity/birth-destination?facilityId=7&requiredLevel=UNKNOWN",
    );
  });

  it("resolves CAPABILITY_UNKNOWN as a real, distinct answer — never a thrown error", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({
        facility_id: 7,
        required_level: "CEMONC",
        status: "CAPABILITY_UNKNOWN",
        operational_gate_passed: true,
        emonc_verdict: "INSUFFICIENT_EVIDENCE",
        call_ahead: true,
        message: "Nothing is known about this facility's emergency obstetric capability.",
        guidance: "Call ahead to confirm.",
      }),
    );
    const verdict = await fetchBirthDestination(7, "CEMONC");
    expect(verdict.status).toBe("CAPABILITY_UNKNOWN");
    expect(verdict.call_ahead).toBe(true);
  });

  it("propagates the 502 UNAVAILABLE failure rather than reporting a safe or unsafe verdict", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "HTTP_502", message: "The facility's status could not be retrieved.", status: 502, correlationId: "c1" }),
    );
    await expect(fetchBirthDestination(7)).rejects.toMatchObject({ status: 502 });
  });
});

describe("getMaternitySummary — never an empty record on failure", () => {
  it("requests with patientId and an optional encounterId", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ patient_id: "P1", partograph_active: false, ctg_active: false, observation_count: 0, last_observed_at: null }),
    );
    await getMaternitySummary("P1", "E1");
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/maternity/summary?patientId=P1&encounterId=E1");
  });

  it("surfaces partograph/ctg activity and observation counts", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({
        patient_id: "P1",
        partograph_active: true,
        partograph_session_id: "S1",
        progress: { status: "LEFT_OF_ALERT" },
        ctg_active: false,
        observation_count: 4,
        last_observed_at: "2026-07-30T10:00:00Z",
      }),
    );
    const summary = await getMaternitySummary("P1");
    expect(summary.partograph_active).toBe(true);
    expect(summary.observation_count).toBe(4);
  });

  it("propagates a maternity_summary_unavailable failure rather than an empty summary", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "maternity_summary_unavailable", message: "down", status: 502, correlationId: "c1" }),
    );
    await expect(getMaternitySummary("P1")).rejects.toMatchObject({ code: "maternity_summary_unavailable" });
  });
});

describe("confidential reproductive reads — empty vs 502 (W13-B)", () => {
  it("fetchContraceptionCoverage returns an empty array when data is null (withhold)", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok(null));
    const rows = await fetchContraceptionCoverage("CP-1");
    expect(rows).toEqual([]);
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/confidential/reproductive/contraception/CP-1");
  });

  it("fetchContraceptionHistory passes the patient CPID on the clinician path", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([{ contraceptive_episode_id: "H-1" }]));
    const rows = await fetchContraceptionHistory("CP-2");
    expect(rows).toHaveLength(1);
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/contraception/CP-2/history",
    );
  });

  it("propagates PCT_UNAVAILABLE rather than collapsing to an empty list", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(fetchPregnancyLosses("CP-3")).rejects.toMatchObject({ code: "PCT_UNAVAILABLE" });
  });

  it("fetchTopAuthorisations reads the confidential lane for a subject CPID", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([{ authorisation_id: "A-1", status: "APPROVED" }]));
    const rows = await fetchTopAuthorisations("CP-4");
    expect(rows[0]?.authorisation_id).toBe("A-1");
  });

  it("fetchPatientPregnancyEpisodes uses the maternity confidential lane with patient CPID", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([{ pregnancy_episode_id: "E-1", status: "ONGOING" }]));
    const episodes = await fetchPatientPregnancyEpisodes("CP-5");
    expect(episodes[0]?.pregnancy_episode_id).toBe("E-1");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/confidential/maternity/pregnancy-episodes/CP-5",
    );
  });
});

describe("W14-B confidential reproductive reads", () => {
  it("fetchCurrentReproductiveIntention hits the confidential reproductive lane", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      ok({ intention_id: "I-1", subject_cpid: "CP-6", intention: "UNDECIDED" }),
    );
    const row = await fetchCurrentReproductiveIntention("CP-6");
    expect(row?.intention).toBe("UNDECIDED");
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/reproductive-intentions/CP-6/current",
    );
  });

  it("fetchDeliveryRecordsForMother treats null data as empty withhold list", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok(null));
    const rows = await fetchDeliveryRecordsForMother("CP-7");
    expect(rows).toEqual([]);
  });

  it("propagates PCT_UNAVAILABLE on W14 reads", async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    await expect(fetchCurrentReproductiveIntention("CP-8")).rejects.toMatchObject({
      code: "PCT_UNAVAILABLE",
    });
  });
});

describe("W14-B confidential reproductive writes", () => {
  it("recordReproductiveIntention posts to the confidential reproductive lane", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ intention_id: "I-1", subject_cpid: "CP-1", intention: "WANTS_PREGNANCY_LATER" }),
    );
    const row = await recordReproductiveIntention({
      subjectCpid: "CP-1",
      intention: "WANTS_PREGNANCY_LATER",
      recordedBy: "prov-1",
    });
    expect(row.intention).toBe("WANTS_PREGNANCY_LATER");
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/reproductive-intentions",
      { subjectCpid: "CP-1", intention: "WANTS_PREGNANCY_LATER", recordedBy: "prov-1" },
    );
  });

  it("openPreconceptionPlan posts to the confidential reproductive lane", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ preconception_plan_id: "PP-1", subject_cpid: "CP-2", status: "ACTIVE" }),
    );
    const plan = await openPreconceptionPlan({ subjectCpid: "CP-2", recordedBy: "prov-1" });
    expect(plan.preconception_plan_id).toBe("PP-1");
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/preconception-plans",
      { subjectCpid: "CP-2", recordedBy: "prov-1" },
    );
  });

  it("startFertilityEpisode posts monthsTrying when supplied", async () => {
    vi.mocked(apiClient.post).mockResolvedValue(
      ok({ fertility_episode_id: "FE-1", subject_cpid: "CP-3", status: "OPEN", months_trying: 14 }),
    );
    const episode = await startFertilityEpisode({
      subjectCpid: "CP-3",
      monthsTrying: 14,
      recordedBy: "prov-1",
    });
    expect(episode.months_trying).toBe(14);
    expect(apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/confidential/reproductive/fertility-episodes",
      { subjectCpid: "CP-3", monthsTrying: 14, recordedBy: "prov-1" },
    );
  });

  it("recordDeliveryRecord propagates the 409 DELIVERY_ALREADY_RECORDED conflict as-is", async () => {
    vi.mocked(apiClient.post).mockRejectedValue(
      new ApiError({
        code: "DELIVERY_ALREADY_RECORDED",
        message: "already recorded",
        status: 409,
        correlationId: "c1",
      }),
    );
    await expect(
      recordDeliveryRecord({
        motherCpid: "CP-4",
        deliveredAt: "2026-07-31T08:00:00Z",
        deliveryMode: "SPONTANEOUS_VAGINAL",
        recordedBy: "prov-1",
      }),
    ).rejects.toMatchObject({ status: 409, code: "DELIVERY_ALREADY_RECORDED" });
  });
});
