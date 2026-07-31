/**
 * Surgical episode spine + course-of-care — BFF `/internal/v1/surgery/**`.
 *
 * Surgery-service is the SoR (store). Responses are forwarded verbatim by the BFF
 * (not wrapped in `{ data: … }`), so callers take `response.data` as the body.
 *
 * Honesty: a failed list must never be collapsed to `[]` by the caller — throw and
 * let React Query surface `isError`. Assessment / decision / follow-up GET 404 means
 * "not recorded yet", not failure (`isNotRecorded`).
 */
import { apiClient, ApiError } from "@impilo/mobile-api-client";

const EPISODES = "/internal/v1/surgery/episodes";

export type SurgicalEpisodeStatus =
  | "ASSESSMENT"
  | "LISTED_FOR_SURGERY"
  | "OPERATED"
  | "CLOSED"
  | "ABANDONED"
  | "REOPENED";

export interface SurgicalEpisode {
  id: string;
  subjectCpid: string;
  journeyId: string | null;
  encounterId: string | null;
  procedureEpisodeRef: string | null;
  pctProblemRef: string | null;
  operativeIndication: string | null;
  nonOperativeOptionsConsidered: string | null;
  status: SurgicalEpisodeStatus;
  specialty: string | null;
  pctContributed: boolean;
  reoperationOfEpisodeId?: string | null;
  reopenedAt?: string | null;
  reopenedBy?: string | null;
  reopenReason?: string | null;
}

export interface SurgicalAssessment {
  id?: string;
  surgicalEpisodeId?: string;
  presentingProblem?: string | null;
  symptomTimeline?: string | null;
  examinationFindings?: string | null;
  differentialDiagnosisNotes?: string | null;
  surgicalRiskAssessment?: string | null;
  woundHealingHistory?: string | null;
  bleedingThrombosisHistory?: string | null;
  infectionHistory?: string | null;
  anaestheticHistoryNotes?: string | null;
  previousSurgeryNotes?: string | null;
  allergyReviewNotes?: string | null;
  medicationReconciliationNotes?: string | null;
  nutritionAssessment?: string | null;
  frailtyNotes?: string | null;
  socialSupportNotes?: string | null;
  transportPlan?: string | null;
  workLivelihoodImpact?: string | null;
  patientGoals?: string | null;
  previousSurgeryReviewed?: boolean;
  allergiesReviewed?: boolean;
  medicationsReviewed?: boolean;
  assessedBy?: string | null;
  assessedAt?: string | null;
  [key: string]: unknown;
}

export type SurgicalFinalDecision = "PROCEED" | "DO_NOT_PROCEED" | "DEFER";
export type SurgicalDecisionForum = "INDIVIDUAL" | "MDT";
export type MdtDecisionSource = "PCT_MDT_DECISION" | "PCT_MDT_CASE_ITEM";

export interface SurgicalDecision {
  id?: string;
  surgicalEpisodeId?: string;
  naturalHistory?: string | null;
  expectedBenefit?: string | null;
  materialRisksConsidered?: string | null;
  anaestheticImplications?: string | null;
  bloodImplications?: string | null;
  functionalImplications?: string | null;
  fertilityImplications?: string | null;
  stomaPossibilityNotes?: string | null;
  implantPossibilityNotes?: string | null;
  rehabilitationExpectation?: string | null;
  financialAccessImplications?: string | null;
  patientPreference?: string | null;
  finalDecision?: SurgicalFinalDecision | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionForum?: SurgicalDecisionForum | null;
  mdtDecisionRef?: string | null;
  mdtDecisionSource?: MdtDecisionSource | null;
  mdtDecisionVerifiedAt?: string | null;
  mdtDecisionVerified?: boolean;
  [key: string]: unknown;
}

export interface EpisodeSpecialty {
  id: string;
  specialty: string;
  role: "LEAD" | "SHARED";
  contribution: string | null;
  addedBy: string;
  addedAt: string;
}

export interface PrehabItem {
  id: string;
  surgicalEpisodeId: string;
  domain: string;
  status?: string | null;
  target?: string | null;
  notes?: string | null;
}

export interface ComplicationPathway {
  id: string;
  surgicalEpisodeId: string;
  complicationType: string;
  recognisedBy: string;
  recognisedAt: string;
  gradeCode?: string | null;
  status: "OPEN" | "CLOSED";
  outcome?: string | null;
}

export interface LongitudinalObject {
  id: string;
  surgicalEpisodeId: string;
  kind: string;
  site: string;
  placedAt: string;
  indication: string;
  status: string;
  operator?: string | null;
}

export interface SurgicalFollowup {
  id: string;
  surgicalEpisodeId: string;
  restrictions?: string | null;
  fitNoteNotes?: string | null;
  transportArranged: boolean;
  transportNotes?: string | null;
  futureSurgeryIntent?: string | null;
  recordedBy?: string | null;
  recordedAt?: string | null;
}

export interface WaitlistRevalidation {
  id: string;
  surgicalEpisodeId: string;
  revalidatedAt: string;
  revalidatedBy: string;
  stillClinicallyAppropriate: boolean;
  notes?: string | null;
  nextRevalidationDue?: string | null;
}

/** True when a query error is the 404 "not recorded yet" gap rather than a real failure. */
export function isNotRecorded(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

export async function listSurgicalEpisodes(subjectCpid: string): Promise<SurgicalEpisode[]> {
  const res = await apiClient.get<SurgicalEpisode[]>(
    `${EPISODES}?subjectCpid=${encodeURIComponent(subjectCpid)}`,
    { noRetry: true },
  );
  return Array.isArray(res.data) ? res.data : [];
}

export async function getSurgicalEpisode(episodeId: string): Promise<SurgicalEpisode> {
  return (await apiClient.get<SurgicalEpisode>(`${EPISODES}/${episodeId}`)).data;
}

export async function openSurgicalEpisode(body: Record<string, unknown>): Promise<SurgicalEpisode> {
  return (await apiClient.post<SurgicalEpisode>(EPISODES, body)).data;
}

export async function transitionSurgicalEpisode(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalEpisode> {
  return (await apiClient.post<SurgicalEpisode>(`${EPISODES}/${episodeId}/transition`, body)).data;
}

export async function reopenSurgicalEpisode(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalEpisode> {
  return (await apiClient.post<SurgicalEpisode>(`${EPISODES}/${episodeId}/reopen`, body)).data;
}

export async function getSurgicalAssessment(episodeId: string): Promise<SurgicalAssessment> {
  return (
    await apiClient.get<SurgicalAssessment>(`${EPISODES}/${episodeId}/assessment`, { noRetry: true })
  ).data;
}

export async function recordSurgicalAssessment(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalAssessment> {
  return (await apiClient.put<SurgicalAssessment>(`${EPISODES}/${episodeId}/assessment`, body)).data;
}

export async function getSurgicalDecision(episodeId: string): Promise<SurgicalDecision> {
  return (
    await apiClient.get<SurgicalDecision>(`${EPISODES}/${episodeId}/decision`, { noRetry: true })
  ).data;
}

export async function recordSurgicalDecision(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalDecision> {
  return (await apiClient.put<SurgicalDecision>(`${EPISODES}/${episodeId}/decision`, body)).data;
}

export async function listEpisodeSpecialties(episodeId: string): Promise<EpisodeSpecialty[]> {
  const res = await apiClient.get<EpisodeSpecialty[]>(`${EPISODES}/${episodeId}/specialties`, {
    noRetry: true,
  });
  return Array.isArray(res.data) ? res.data : [];
}

export async function addEpisodeSpecialty(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<EpisodeSpecialty> {
  return (await apiClient.post<EpisodeSpecialty>(`${EPISODES}/${episodeId}/specialties`, body)).data;
}

export async function transferEpisodeLead(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<EpisodeSpecialty> {
  return (
    await apiClient.post<EpisodeSpecialty>(`${EPISODES}/${episodeId}/specialties/lead`, body)
  ).data;
}

export async function removeEpisodeSpecialty(episodeId: string, specialty: string): Promise<void> {
  await apiClient.delete(
    `${EPISODES}/${episodeId}/specialties?specialty=${encodeURIComponent(specialty)}`,
  );
}

export async function listPrehabItems(episodeId: string): Promise<PrehabItem[]> {
  const res = await apiClient.get<PrehabItem[]>(`${EPISODES}/${episodeId}/prehab`, { noRetry: true });
  return Array.isArray(res.data) ? res.data : [];
}

export async function recordPrehabItem(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<PrehabItem> {
  return (await apiClient.put<PrehabItem>(`${EPISODES}/${episodeId}/prehab`, body)).data;
}

export async function listComplications(episodeId: string): Promise<ComplicationPathway[]> {
  const res = await apiClient.get<ComplicationPathway[]>(`${EPISODES}/${episodeId}/complications`, {
    noRetry: true,
  });
  return Array.isArray(res.data) ? res.data : [];
}

export async function recogniseComplication(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<ComplicationPathway> {
  return (
    await apiClient.post<ComplicationPathway>(`${EPISODES}/${episodeId}/complications`, body)
  ).data;
}

export async function updateComplication(
  episodeId: string,
  pathwayId: string,
  body: Record<string, unknown>,
): Promise<ComplicationPathway> {
  return (
    await apiClient.put<ComplicationPathway>(
      `${EPISODES}/${episodeId}/complications/${pathwayId}`,
      body,
    )
  ).data;
}

export async function gradeComplication(
  episodeId: string,
  pathwayId: string,
  body: Record<string, unknown>,
): Promise<ComplicationPathway> {
  return (
    await apiClient.post<ComplicationPathway>(
      `${EPISODES}/${episodeId}/complications/${pathwayId}/grade`,
      body,
    )
  ).data;
}

export async function discloseComplication(
  episodeId: string,
  pathwayId: string,
  body: Record<string, unknown> = {},
): Promise<ComplicationPathway> {
  return (
    await apiClient.post<ComplicationPathway>(
      `${EPISODES}/${episodeId}/complications/${pathwayId}/disclose`,
      body,
    )
  ).data;
}

export async function closeComplication(
  episodeId: string,
  pathwayId: string,
  body: Record<string, unknown> = {},
): Promise<ComplicationPathway> {
  return (
    await apiClient.post<ComplicationPathway>(
      `${EPISODES}/${episodeId}/complications/${pathwayId}/close`,
      body,
    )
  ).data;
}

export async function listLongitudinalObjects(episodeId: string): Promise<LongitudinalObject[]> {
  const res = await apiClient.get<LongitudinalObject[]>(
    `${EPISODES}/${episodeId}/longitudinal-objects`,
    { noRetry: true },
  );
  return Array.isArray(res.data) ? res.data : [];
}

export async function placeLongitudinalObject(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<LongitudinalObject> {
  return (
    await apiClient.post<LongitudinalObject>(`${EPISODES}/${episodeId}/longitudinal-objects`, body)
  ).data;
}

export async function removeLongitudinalObject(
  episodeId: string,
  objectId: string,
  body: Record<string, unknown> = {},
): Promise<LongitudinalObject> {
  return (
    await apiClient.post<LongitudinalObject>(
      `${EPISODES}/${episodeId}/longitudinal-objects/${objectId}/remove`,
      body,
    )
  ).data;
}

export async function reviseLongitudinalObject(
  episodeId: string,
  objectId: string,
  body: Record<string, unknown>,
): Promise<LongitudinalObject> {
  return (
    await apiClient.post<LongitudinalObject>(
      `${EPISODES}/${episodeId}/longitudinal-objects/${objectId}/revise`,
      body,
    )
  ).data;
}

export async function linkProcedureEpisode(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalEpisode> {
  return (
    await apiClient.post<SurgicalEpisode>(`${EPISODES}/${episodeId}/link-procedure-episode`, body)
  ).data;
}

/** Specialty catalogue indications — programme spine, not episode specialties. */
export async function listSurgerySpecialtyIndications(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>("/internal/v1/surgery/specialties/indications", {
    noRetry: true,
  });
  return Array.isArray(res.data) ? (res.data as Record<string, unknown>[]) : [];
}

export async function listSurgerySpecialtyTemplates(): Promise<Record<string, unknown>[]> {
  const res = await apiClient.get<unknown>("/internal/v1/surgery/specialties/templates", {
    noRetry: true,
  });
  return Array.isArray(res.data) ? (res.data as Record<string, unknown>[]) : [];
}

export async function listSurgeryAnalyticsIndicators(): Promise<Record<string, unknown>> {
  return (
    await apiClient.get<Record<string, unknown>>("/internal/v1/surgery/analytics/indicators", {
      noRetry: true,
    })
  ).data;
}

export async function getSurgicalFollowup(episodeId: string): Promise<SurgicalFollowup> {
  return (
    await apiClient.get<SurgicalFollowup>(`${EPISODES}/${episodeId}/followup`, { noRetry: true })
  ).data;
}

export async function recordSurgicalFollowup(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<SurgicalFollowup> {
  return (await apiClient.put<SurgicalFollowup>(`${EPISODES}/${episodeId}/followup`, body)).data;
}

export async function listWaitlistRevalidations(episodeId: string): Promise<WaitlistRevalidation[]> {
  const res = await apiClient.get<WaitlistRevalidation[]>(
    `${EPISODES}/${episodeId}/waitlist-revalidation`,
    { noRetry: true },
  );
  return Array.isArray(res.data) ? res.data : [];
}

export async function appendWaitlistRevalidation(
  episodeId: string,
  body: Record<string, unknown>,
): Promise<WaitlistRevalidation> {
  return (
    await apiClient.post<WaitlistRevalidation>(
      `${EPISODES}/${episodeId}/waitlist-revalidation`,
      body,
    )
  ).data;
}
