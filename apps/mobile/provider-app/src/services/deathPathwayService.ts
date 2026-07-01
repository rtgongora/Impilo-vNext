/**
 * Death Pathway Service — Provider field slice (WS#8 + Community / Field Death Pathway refinement,
 * docs/clinical/community-field-death-pathway.md).
 *
 * Backend: experience-bff (/internal/v1/death/*) → pct-service (DeathCase owner) + ubomi-service
 * (CRVS owner). PCT owns the DeathCase, Butano owns the clinical record, Ubomi owns registration —
 * this slice never duplicates those records. Death documentation is never payment-gated.
 *
 * Contract decision (why the endpoints below): this is the PROVIDER app. When a provider is present
 * they CONFIRM a death — a confirmation, not a report:
 *   • facility death                → confirmDeath()          → POST /confirm   (sourceContext FACILITY)
 *   • provider-confirmed field death → confirmFieldDeath()    → POST /confirm   (sourceContext COMMUNITY_FIELD
 *                                                                                + bodyDispositionContext)
 *   • body brought in dead           → reportBroughtInDead()  → POST /brought-in-dead
 * A community death relayed to the provider WITHOUT a provider having confirmed it is an unverified
 * REPORT, which is a distinct thing:
 *   • unverified community report     → reportCommunityDeath() → POST /community-report
 * Two follow-on branches attach to an existing case:
 *   • verbal-autopsy interview        → recordVerbalAutopsy()  → POST /cases/{id}/verbal-autopsy
 *   • non-facility body management    → recordFieldBodyManagement() → POST /cases/{id}/field-body-management
 *
 * Community/field death ≠ brought-in-dead ≠ facility confirmation; verbal autopsy ≠ medical
 * certification. These are kept distinct end-to-end.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/death";

/** Where the death originated. A community/field death may never come to a facility. */
export type SourceContext =
  | "FACILITY"
  | "BROUGHT_IN_DEAD"
  | "COMMUNITY_HOME"
  | "COMMUNITY_FIELD"
  | "OUTREACH_FIELD_OPS"
  | "DISASTER_SITE"
  | "ISOLATION_FIELD_SITE"
  | "CUSTODY"
  | "UNKNOWN_LOCATION";

/** Where the body goes — custody is no longer mandatory-to-facility. */
export type BodyDispositionContext =
  | "BROUGHT_TO_FACILITY"
  | "NOT_BROUGHT_TO_FACILITY"
  | "FIELD_SAFE_BURIAL"
  | "COMMUNITY_RELEASE"
  | "POLICE_CUSTODY"
  | "FUNERAL_DIRECTOR_DIRECT"
  | "TRANSFERRED_TO_MORTUARY"
  | "TRANSFERRED_TO_POST_MORTEM_SITE";

/** Certainty class of the cause — verbal-autopsy-probable is never treated as medically certified. */
export type CauseOfDeathBasis =
  | "MEDICALLY_CERTIFIED"
  | "POST_MORTEM_CERTIFIED"
  | "VERBAL_AUTOPSY_PROBABLE"
  | "FIELD_INVESTIGATION_PROBABLE"
  | "MEDICO_LEGAL_PENDING"
  | "UNKNOWN_UNCERTIFIED";

export interface DeathCaseSummary {
  caseId?: string;
  id?: string;
  patientCpid?: string;
  deceasedIdentityStatus?: string;
  placeOfDeathContext?: string;
  sourceContext?: SourceContext;
  bodyDispositionContext?: BodyDispositionContext;
  causeOfDeathBasis?: CauseOfDeathBasis;
  status?: string;
  confirmedAt?: string;
  coronerReferralRequired?: boolean;
  medicoLegalTriggers?: string;
  certificationStatus?: string;
  civilRegistrationStatus?: string;
}

export interface ConfirmDeathInput {
  deathDatetime: string;
  placeOfDeathContext: string; // INPATIENT|ED|THEATRE|COMMUNITY_FIELD|COMMUNITY_HOME|BROUGHT_IN_DEAD|…
  placeOfDeathLocation?: string;
  deceasedCpid?: string;
  deceasedIdentityStatus?: string; // KNOWN|UNKNOWN|TEMPORARY
  resuscitationAttempted?: boolean;
  presentAtDeath?: string;
  suspectedManner?: string;
  inCustody?: boolean;
  sourceContext?: SourceContext;
  bodyDispositionContext?: BodyDispositionContext;
}

export function caseId(c: DeathCaseSummary): string {
  return c.caseId ?? c.id ?? "";
}

/** Death cases for the provider's facility. */
export async function listDeathCases(): Promise<DeathCaseSummary[]> {
  const res = await apiClient.get<DeathCaseSummary[]>(`${V1}/cases`);
  return Array.isArray(res.data) ? res.data : [];
}

/** Confirm a death (facility or provider-confirmed field). Runs the medico-legal screen server-side. */
export async function confirmDeath(input: ConfirmDeathInput): Promise<DeathCaseSummary> {
  const res = await apiClient.post<DeathCaseSummary>(`${V1}/confirm`, input);
  return res.data;
}

/**
 * Provider-CONFIRMED field / community / outreach / disaster / isolation death. The provider is
 * present and confirms; the body may never come to a facility. Defaults to COMMUNITY_FIELD and a
 * not-brought-to-facility disposition — override sourceContext / bodyDispositionContext for home,
 * outreach, disaster, isolation, safe-burial, etc. Routed via /confirm (a confirmation, not a report).
 */
export async function confirmFieldDeath(
  input: Omit<ConfirmDeathInput, "placeOfDeathContext"> & { placeOfDeathContext?: string }
): Promise<DeathCaseSummary> {
  return confirmDeath({
    ...input,
    placeOfDeathContext: input.placeOfDeathContext ?? "COMMUNITY_FIELD",
    sourceContext: input.sourceContext ?? "COMMUNITY_FIELD",
    bodyDispositionContext: input.bodyDispositionContext ?? "NOT_BROUGHT_TO_FACILITY",
  });
}

/**
 * A body brought in dead to the facility / mortuary / police point. Facility body receipt, mortuary
 * custody, and medico-legal screening apply — this is NOT a community/field death. The server forces
 * the brought-in-dead place + source; unknown bodies mint a temporary deceased identity server-side.
 */
export async function reportBroughtInDead(
  input: Omit<ConfirmDeathInput, "placeOfDeathContext" | "sourceContext">
): Promise<DeathCaseSummary> {
  const res = await apiClient.post<DeathCaseSummary>(`${V1}/brought-in-dead`, input);
  return res.data;
}

export interface CommunityDeathReportInput {
  deathDatetime: string;
  sourceContext?: SourceContext; // a community/field origin; defaults COMMUNITY_FIELD
  placeOfDeathContext?: string;
  placeOfDeathLocation?: string;
  deceasedCpid?: string;
  deceasedIdentityStatus?: string;
  suspectedManner?: string;
  inCustody?: boolean;
  bodyDispositionContext?: BodyDispositionContext;
  reporterName?: string;
  reporterRole?: string; // CHW|FAMILY|SURVEILLANCE|COMMUNITY_LEADER|POLICE|OTHER
  reporterContact?: string;
}

/**
 * Report an UNVERIFIED community / field death — relayed from a CHW, family, or surveillance team
 * without a provider having confirmed it. The case opens in status REPORTED with no asserted cause;
 * a red-flag manner still routes it to the medico-legal pathway. This is NOT a confirmation.
 */
export async function reportCommunityDeath(
  input: CommunityDeathReportInput
): Promise<DeathCaseSummary> {
  const res = await apiClient.post<DeathCaseSummary>(`${V1}/community-report`, {
    sourceContext: "COMMUNITY_FIELD",
    ...input,
  });
  return res.data;
}

/** Fetch a single case. */
export async function getDeathCase(id: string): Promise<DeathCaseSummary> {
  const res = await apiClient.get<DeathCaseSummary>(`${V1}/cases/${id}`);
  return res.data;
}

export interface VerbalAutopsyInput {
  instrument?: string; // WHO_VA_2022|WHO_VA_2016|OTHER
  intervieweeRelationship?: string;
  interviewedAt?: string;
  narrative?: string;
  probableImmediateCause?: string;
  probableUnderlyingCause?: string;
  probableCauseCode?: string;
  causeAssignmentMethod?: string;
  redFlags?: string[];
  complete?: boolean;
}

/**
 * Record a verbal-autopsy interview against a community/field death case (WHO VA standard). Yields a
 * PROBABLE cause only — never a medical certification. Red flags route the case to the coroner /
 * investigation pathway instead of assigning a probable cause.
 */
export async function recordVerbalAutopsy(
  id: string,
  input: VerbalAutopsyInput
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<Record<string, unknown>>(`${V1}/cases/${id}/verbal-autopsy`, input);
  return res.data;
}

export interface FieldBodyManagementInput {
  dispositionType: string; // SAFE_AND_DIGNIFIED_BURIAL|FAMILY_RELEASE|FUNERAL_DIRECTOR|POLICE_HANDOVER|UNRECOVERED
  infectiousRisk?: boolean;
  infectiousHazard?: string;
  safeBurialProtocolApplied?: boolean;
  ppeUsed?: boolean;
  handlingTeam?: string;
  familyPresent?: boolean;
  religiousRitesObserved?: boolean;
  releasedToName?: string;
  releasedToRelationship?: string;
  location?: string;
  occurredAt?: string;
  notes?: string;
}

/**
 * Record non-facility (field) body management against a case — safe &amp; dignified burial, family
 * release, funeral-director direct, police handover, or unrecovered. No mortuary custody is required;
 * a non-police disposition is refused server-side while an open medico-legal referral is present.
 */
export async function recordFieldBodyManagement(
  id: string,
  input: FieldBodyManagementInput
): Promise<Record<string, unknown>> {
  const res = await apiClient.post<Record<string, unknown>>(
    `${V1}/cases/${id}/field-body-management`,
    input
  );
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
