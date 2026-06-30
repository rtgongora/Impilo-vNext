/**
 * Death Pathway Service — Provider field slice (WS#8).
 *
 * Backend: experience-bff (/internal/v1/death/*) → pct-service (DeathCase owner) + ubomi-service
 * (CRVS owner). Lets an authorised provider confirm a death in the facility or in the community /
 * home (brought-in-dead), review the resulting case, and attach supporting documents. PCT owns the
 * DeathCase, Butano owns the clinical record, Ubomi owns registration — this slice never duplicates
 * those records. Death documentation is never payment-gated.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/death";

export interface DeathCaseSummary {
  caseId?: string;
  id?: string;
  patientCpid?: string;
  deceasedIdentityStatus?: string;
  placeOfDeathContext?: string;
  confirmedAt?: string;
  coronerReferralRequired?: boolean;
  medicoLegalTriggers?: string;
  certificationStatus?: string;
  civilRegistrationStatus?: string;
}

export interface ConfirmDeathInput {
  deathDatetime: string;
  placeOfDeathContext: string; // INPATIENT|ED|THEATRE|COMMUNITY|BROUGHT_IN_DEAD|IN_TRANSIT|OTHER
  placeOfDeathLocation?: string;
  deceasedCpid?: string;
  deceasedIdentityStatus?: string; // KNOWN|UNKNOWN|TEMPORARY
  resuscitationAttempted?: boolean;
  presentAtDeath?: string;
  suspectedManner?: string;
  sourceContext?: string; // FACILITY|COMMUNITY
}

export function caseId(c: DeathCaseSummary): string {
  return c.caseId ?? c.id ?? "";
}

/** Death cases for the provider's facility. */
export async function listDeathCases(): Promise<DeathCaseSummary[]> {
  const res = await apiClient.get<DeathCaseSummary[]>(`${V1}/cases`);
  return Array.isArray(res.data) ? res.data : [];
}

/** Confirm a death (facility or community/brought-in-dead). Runs the medico-legal screen server-side. */
export async function confirmDeath(input: ConfirmDeathInput): Promise<DeathCaseSummary> {
  const res = await apiClient.post<DeathCaseSummary>(`${V1}/confirm`, input);
  return res.data;
}

/**
 * Confirm a community / home death report. Place context defaults to COMMUNITY (or BROUGHT_IN_DEAD)
 * and the source is COMMUNITY so the case is routed appropriately; unknown bodies mint a temporary
 * deceased identity server-side.
 */
export async function reportCommunityDeath(
  input: Omit<ConfirmDeathInput, "placeOfDeathContext" | "sourceContext"> & { broughtInDead?: boolean }
): Promise<DeathCaseSummary> {
  return confirmDeath({
    ...input,
    placeOfDeathContext: input.broughtInDead ? "BROUGHT_IN_DEAD" : "COMMUNITY",
    sourceContext: "COMMUNITY",
  });
}

/** Fetch a single case. */
export async function getDeathCase(id: string): Promise<DeathCaseSummary> {
  const res = await apiClient.get<DeathCaseSummary>(`${V1}/cases/${id}`);
  return res.data;
}

export interface SupportingDocRef {
  /** Document-service reference for the already-uploaded file (e.g. an ID photo, referral note). */
  documentRef: string;
  documentType?: string;
  description?: string;
}

/**
 * Link a supporting document to the death case. The file itself is uploaded to the document service
 * separately (that binary-upload path is owner-routed and not duplicated here); this records the
 * resulting reference against the case so PCT can associate it. Returns the stored reference.
 */
export async function attachSupportingDoc(id: string, doc: SupportingDocRef): Promise<{ documentRef?: string }> {
  const res = await apiClient.post<{ documentRef?: string }>(`${V1}/cases/${id}/documents`, doc);
  return res.data;
}
