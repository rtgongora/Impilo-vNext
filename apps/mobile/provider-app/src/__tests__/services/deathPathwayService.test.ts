/**
 * Provider Death Pathway service tests (WS#8 + Community / Field Death Pathway refinement).
 *
 * Aligned to docs/clinical/community-field-death-pathway.md: community/field death, brought-in-dead,
 * and facility confirmation are DISTINCT endpoints, and a provider CONFIRMATION is distinct from an
 * unverified community REPORT. Verbal autopsy yields a probable cause, never a medical certification.
 * All against the Death BFF; PCT owns the DeathCase, Ubomi owns registration — no record duplication.
 * (Written + export-verified; the mobile workspace does not install in this environment —
 * workspace:* protocol — so this is not run here.)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import {
  listDeathCases,
  confirmDeath,
  confirmFieldDeath,
  reportBroughtInDead,
  reportCommunityDeath,
  recordVerbalAutopsy,
  recordFieldBodyManagement,
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

  // A provider present in the field CONFIRMS the death — a confirmation (/confirm), not a report,
  // with a COMMUNITY_FIELD source and a not-brought-to-facility disposition.
  it("confirms a provider field death via /confirm with COMMUNITY_FIELD source", async () => {
    mockApiClient.post.mockResolvedValue({ data: { caseId: "dc-2", sourceContext: "COMMUNITY_FIELD" } });
    const c = await confirmFieldDeath({
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "KNOWN",
      deceasedCpid: "CPID-9",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/confirm", {
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "KNOWN",
      deceasedCpid: "CPID-9",
      placeOfDeathContext: "COMMUNITY_FIELD",
      sourceContext: "COMMUNITY_FIELD",
      bodyDispositionContext: "NOT_BROUGHT_TO_FACILITY",
    });
    expect(c.sourceContext).toBe("COMMUNITY_FIELD");
  });

  // A body physically brought in dead goes to its OWN endpoint — facility receipt + mortuary apply.
  it("reports a brought-in-dead body via /brought-in-dead", async () => {
    mockApiClient.post.mockResolvedValue({ data: { caseId: "dc-3", deceasedIdentityStatus: "UNKNOWN" } });
    await reportBroughtInDead({ deathDatetime: "2026-06-30T08:00:00Z", deceasedIdentityStatus: "UNKNOWN" });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/brought-in-dead", {
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "UNKNOWN",
    });
  });

  // An UNVERIFIED community death relayed by a CHW/family/surveillance is a REPORT, not a confirmation.
  it("reports an unverified community death via /community-report", async () => {
    mockApiClient.post.mockResolvedValue({ data: { caseId: "dc-4", status: "REPORTED" } });
    const c = await reportCommunityDeath({
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "UNKNOWN",
      reporterRole: "CHW",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/community-report", {
      sourceContext: "COMMUNITY_FIELD",
      deathDatetime: "2026-06-30T08:00:00Z",
      deceasedIdentityStatus: "UNKNOWN",
      reporterRole: "CHW",
    });
    expect(c.status).toBe("REPORTED");
  });

  // Verbal autopsy yields a PROBABLE cause against an existing case — never a medical certification.
  it("records a verbal autopsy (probable cause) against a case", async () => {
    mockApiClient.post.mockResolvedValue({ data: { vaId: "va-1", status: "COMPLETED" } });
    const r = await recordVerbalAutopsy("dc-4", {
      probableUnderlyingCause: "Malaria",
      causeAssignmentMethod: "PHYSICIAN_REVIEW",
      complete: true,
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/cases/dc-4/verbal-autopsy", {
      probableUnderlyingCause: "Malaria",
      causeAssignmentMethod: "PHYSICIAN_REVIEW",
      complete: true,
    });
    expect(r.status).toBe("COMPLETED");
  });

  // Non-facility body management — safe & dignified burial, no mortuary custody.
  it("records field body management against a case", async () => {
    mockApiClient.post.mockResolvedValue({ data: { fbmId: "fbm-1", dispositionType: "SAFE_AND_DIGNIFIED_BURIAL" } });
    await recordFieldBodyManagement("dc-4", {
      dispositionType: "SAFE_AND_DIGNIFIED_BURIAL",
      infectiousRisk: true,
      safeBurialProtocolApplied: true,
    });
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/death/cases/dc-4/field-body-management", {
      dispositionType: "SAFE_AND_DIGNIFIED_BURIAL",
      infectiousRisk: true,
      safeBurialProtocolApplied: true,
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
