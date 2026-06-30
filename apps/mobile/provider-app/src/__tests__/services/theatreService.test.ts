/**
 * Provider theatre & perioperative service tests (WS#6 theatre seam). All calls go to the BFF, which
 * proxies inpatient-service's /internal/v1/theatre/** module — the mobile app duplicates no record.
 * Asserts the exact endpoints + owner-routed safety/death wiring.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import {
  listTheatreQueue, intakeTheatreCase, setTheatreTriage, evaluateTheatreReadiness,
  bookTheatreCase, startTheatreCase, draftTheatreNote, signTheatreNote,
  recordTheatrePacuDisposition, cancelTheatreCase, reportTheatreSafetyEvent,
  listTheatreSafetyEvents, routeTheatreDeath,
} from "../../services/procedureService";

describe("theatre service (provider mobile)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists the theatre queue", async () => {
    mockApiClient.get.mockResolvedValue({ data: { data: [{ id: "c1", triage_priority: "URGENT" }] } });
    const q = await listTheatreQueue();
    expect(q).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/theatre/queue");
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
});
