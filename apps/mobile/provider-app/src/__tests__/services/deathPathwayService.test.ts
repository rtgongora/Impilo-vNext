/**
 * Provider Death Pathway service tests (WS#8) — confirm a death, community/brought-in-dead report,
 * list/read cases, attach a supporting document reference. All against the Death BFF; PCT owns the
 * DeathCase, Ubomi owns registration — no record duplication. (Written + export-verified; the mobile
 * workspace does not install in this environment — workspace:* protocol — so this is not run here.)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import {
  listDeathCases,
  confirmDeath,
  reportCommunityDeath,
  getDeathCase,
  attachSupportingDoc,
  caseId,
} from "../../services/deathPathwayService";

describe("deathPathwayService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists death cases for the facility", async () => {
    mockApiClient.get.mockResolvedValue({ data: [{ caseId: "dc-1", certificationStatus: "DRAFT" }] });
    const list = await listDeathCases();
    expect(list).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/death/cases");
  });

  it("confirms a facility death (server runs the medico-legal screen)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { caseId: "dc-1", coronerReferralRequired: false } });
    const c = await confirmDeath({ deathDatetime: "2026-06-30T10:00:00Z", placeOfDeathContext: "INPATIENT" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/confirm", {
      deathDatetime: "2026-06-30T10:00:00Z",
      placeOfDeathContext: "INPATIENT",
    });
    expect(c.caseId).toBe("dc-1");
  });

  it("reports a community/brought-in-dead death with COMMUNITY source", async () => {
    mockApiClient.post.mockResolvedValue({ data: { caseId: "dc-2", deceasedIdentityStatus: "UNKNOWN" } });
    await reportCommunityDeath({ deathDatetime: "2026-06-30T08:00:00Z", deceasedIdentityStatus: "UNKNOWN", broughtInDead: true });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/confirm", {
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "UNKNOWN",
      placeOfDeathContext: "BROUGHT_IN_DEAD",
      sourceContext: "COMMUNITY",
    });
  });

  it("reads a single case", async () => {
    mockApiClient.get.mockResolvedValue({ data: { caseId: "dc-1" } });
    const c = await getDeathCase("dc-1");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/death/cases/dc-1");
    expect(caseId(c)).toBe("dc-1");
  });

  it("attaches a supporting document reference to the case", async () => {
    mockApiClient.post.mockResolvedValue({ data: { documentRef: "DOC-9" } });
    const r = await attachSupportingDoc("dc-1", { documentRef: "DOC-9", documentType: "ID_PHOTO" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/cases/dc-1/documents", {
      documentRef: "DOC-9",
      documentType: "ID_PHOTO",
    });
    expect(r.documentRef).toBe("DOC-9");
  });
});
