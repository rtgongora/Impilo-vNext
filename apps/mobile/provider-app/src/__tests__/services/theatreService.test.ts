/**
 * Provider theatre & perioperative service tests (WS#6 theatre seam + Wave M2 OR panels).
 * All calls go to the BFF, which proxies inpatient-service's /internal/v1/theatre/** module —
 * the mobile app duplicates no record. Asserts exact endpoints + owner-routed safety/death wiring.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import {
  listTheatreQueue, getTheatreCase, intakeTheatreCase, setTheatreTriage, evaluateTheatreReadiness,
  bookTheatreCase, startTheatreCase, draftTheatreNote, signTheatreNote,
  recordTheatrePacuDisposition, cancelTheatreCase, reportTheatreSafetyEvent,
  listTheatreSafetyEvents, routeTheatreDeath,
  confirmTheatreSiteSide, listTheatreSpecimens, collectTheatreSpecimen,
  confirmTheatreSpecimenLabel, receiveTheatreSpecimen, assessTheatreSpecimenAdequacy,
  listTheatreCounts, recordTheatreCount, listTheatreImplants, recordTheatreImplant,
  listTheatreBlood, theatreBloodAction, recordTheatreEmergencyConsentException,
  recordReturnToTheatre,
} from "../../services/procedureService";

describe("theatre service (provider mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists the theatre queue", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "c1", triage_priority: "URGENT" }] } });
    const q = await listTheatreQueue();
    expect(q).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/queue");
  });

  it("loads theatre case detail (case id = procedure episode id)", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { id: "c1", procedure_name: "Appendectomy", status: "READY" } },
    });
    const c = await getTheatreCase("c1");
    expect((c as { procedure_name: string }).procedure_name).toBe("Appendectomy");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1");
  });

  it("intakes an OROS PROCEDURE order as a theatre case", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "c1" } } });
    const c = await intakeTheatreCase({ patientId: "CPID-1", procedureName: "Appendectomy" });
    expect(c.id).toBe("c1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases",
      { patientId: "CPID-1", procedureName: "Appendectomy" });
  });

  it("sets triage priority", async () => {
    mockApiClient.post.mockResolvedValue({ data: {} });
    await setTheatreTriage("c1", { triagePriority: "EMERGENCY" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/triage",
      { triagePriority: "EMERGENCY" });
  });

  it("evaluates readiness (owner-routed; may be blocked)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { bookable: false, blockers: [{ code: "NO_ROOM" }] } } });
    const r = await evaluateTheatreReadiness("c1");
    expect((r as { bookable: boolean }).bookable).toBe(false);
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/readiness", {});
  });

  it("books a case", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "BOOKED" } } });
    await bookTheatreCase("c1", { emergencyOverride: true, emergencyOverrideReason: "Emergency" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/book",
      { emergencyOverride: true, emergencyOverrideReason: "Emergency" });
  });

  it("starts a case (WHO-checklist gate enforced server-side)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "IN_PROGRESS" } } });
    await startTheatreCase("c1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/start", {});
  });

  it("drafts and signs the operative note (surgical scope checked server-side)", async () => {
    mockApiClient.post.mockResolvedValueOnce({ data: { data: { id: "n1" } } });
    const n = await draftTheatreNote("c1", { performedProcedure: "Appendectomy" });
    expect(n.id).toBe("n1");
    mockApiClient.post.mockResolvedValueOnce({ data: { data: { status: "SIGNED" } } });
    await signTheatreNote("c1", "surgeon-1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/note/sign",
      { signedProviderId: "surgeon-1" });
  });

  it("records PACU disposition", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "RECOVERED" } } });
    await recordTheatrePacuDisposition("c1", { disposition: "WARD", aldreteScore: 9 });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/pacu/disposition",
      { disposition: "WARD", aldreteScore: 9 });
  });

  it("cancels a case with a reason (releases owner reservations)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "CANCELLED" } } });
    await cancelTheatreCase("c1", "Patient not fasted");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/cancel",
      { reason: "Patient not fasted" });
  });

  it("reports a safety event routed to its owner (Rito/Madi/asset-registry)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "s1", routed_owner: "rito" } } });
    const s = await reportTheatreSafetyEvent("c1", { category: "NEAR_MISS", description: "Swab delay" });
    expect(s.id).toBe("s1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/safety-events",
      { category: "NEAR_MISS", description: "Swab delay" });
  });

  it("lists safety events", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "s1", category: "DEATH", routed_owner: "pct-death" }] } });
    const list = await listTheatreSafetyEvents("c1");
    expect(list).toHaveLength(1);
  });

  it("routes a death-in-theatre to the PCT death pathway (never owns the death case)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "DECEASED", death_routed: true } } });
    const r = await routeTheatreDeath("c1", { resuscitationAttempted: true });
    expect((r as { status: string }).status).toBe("DECEASED");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/death",
      { resuscitationAttempted: true });
  });

  // ── Wave M2 bedside OR panels (match web BFF paths) ──────────────────────────

  it("confirms site/side marking", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { laterality: "LEFT" } } });
    await confirmTheatreSiteSide("c1", { laterality: "LEFT", anatomicalSite: "RLQ" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/site-side", {
      laterality: "LEFT",
      anatomicalSite: "RLQ",
    });
  });

  it("lists specimens and walks the custody chain", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "s1" }] } });
    expect(await listTheatreSpecimens("c1")).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/specimens");

    mockApiClient.post.mockResolvedValue({ data: { data: {} } });
    await collectTheatreSpecimen("c1", "s1", { containerType: "POT" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/specimens/s1/collect",
      { containerType: "POT" },
    );
    await confirmTheatreSpecimenLabel("c1", "s1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/specimens/s1/confirm-label",
      {},
    );
    await receiveTheatreSpecimen("c1", "s1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/specimens/s1/receive",
      {},
    );
    await assessTheatreSpecimenAdequacy("c1", "s1", { adequacy: "ADEQUATE" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/specimens/s1/adequacy",
      { adequacy: "ADEQUATE" },
    );
  });

  it("lists and records surgical counts", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ kind: "SWAB", discrepancy: 0 }] } });
    expect(await listTheatreCounts("c1")).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/counts");
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "cnt1" } } });
    await recordTheatreCount("c1", { kind: "SWAB", baselineCount: 10, closingCount: 10 });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/counts", {
      kind: "SWAB",
      baselineCount: 10,
      closingCount: 10,
    });
  });

  it("lists and records implants", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "im1", udi: "UDI-1" }] } });
    expect(await listTheatreImplants("c1")).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/implants");
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "im2" } } });
    await recordTheatreImplant("c1", { udi: "UDI-2" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/implants", {
      udi: "UDI-2",
    });
  });

  it("lists blood and drives request/issue/administer lifecycle", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "b1", status: "REQUESTED" }] } });
    expect(await listTheatreBlood("c1")).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/blood");
    mockApiClient.post.mockResolvedValue({ data: { data: {} } });
    await theatreBloodAction("c1", "request", { units: 2, componentType: "PRBC" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/blood/request", {
      units: 2,
      componentType: "PRBC",
    });
    await theatreBloodAction("c1", "issue");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c1/blood/issue", {});
    await theatreBloodAction("c1", "administer", {
      patientMethod: "WRISTBAND",
      unitMethod: "BARCODE",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/blood/administer",
      { patientMethod: "WRISTBAND", unitMethod: "BARCODE" },
    );
  });

  it("records emergency consent exception (no GRANTED fabricated)", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { consent_status: "EMERGENCY_EXCEPTION" } },
    });
    const r = await recordTheatreEmergencyConsentException("c1", {
      basis: "DEFERRED",
      reason: "Life-threatening haemorrhage",
    });
    expect((r as { consent_status: string }).consent_status).toBe("EMERGENCY_EXCEPTION");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/consent/emergency-exception",
      { basis: "DEFERRED", reason: "Life-threatening haemorrhage" },
    );
  });

  it("records return-to-theatre with complication category", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "READY" } } });
    await recordReturnToTheatre("c1", {
      reason: "Post-op bleed",
      complicationCategory: "HAEMORRHAGE",
      planned: false,
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/c1/return-to-theatre",
      { reason: "Post-op bleed", complicationCategory: "HAEMORRHAGE", planned: false },
    );
  });
});
