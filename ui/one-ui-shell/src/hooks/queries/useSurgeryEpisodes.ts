/**
 * Experience UI — surgical episode (S1) + general surgical assessment (S2) + surgical
 * decision-making record (S3) query hooks.
 *
 * Backed by the experience-bff surgery proxy (`/internal/v1/surgery/episodes/**`), which
 * forwards verbatim to surgery-service (the SoR for these rows — unlike procedures-service
 * this is a store, so mutation hooks exist here).
 *
 * EMPTY VS UNKNOWN VS UNAVAILABLE. `apiClient` throws on a non-2xx response, so a downstream
 * failure surfaces as `isError`/`error` on the query, never as a resolved empty result. One
 * nuance this file owns: assessment/decision GETs return 404 when the record has simply not
 * been written yet — an honest gap, not a failure. `isNotRecorded` distinguishes that from a
 * real fetch failure so a page can render "not recorded yet" for 404 and a genuine error
 * banner for everything else. A consumer that collapses those states (or falls through
 * `?? []` on error) reintroduces the "no conditions for every patient" bug this estate has
 * already shipped once.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type SurgicalEpisodeStatus =
  | "ASSESSMENT"
  | "LISTED_FOR_SURGERY"
  | "OPERATED"
  | "CLOSED"
  | "ABANDONED"
  /** V010 — reopened for a return to theatre. Only reachable via the audited reopen route. */
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
  /** The LEAD specialty. Every specialty on a shared case comes from `useEpisodeSpecialties`. */
  specialty: string | null;
  pctContributed: boolean;
  /** V010 reoperation trail — the predecessor episode, and who reopened this one, when and why. */
  reoperationOfEpisodeId?: string | null;
  reopenedAt?: string | null;
  reopenedBy?: string | null;
  reopenReason?: string | null;
}

export interface OpenEpisodePayload {
  subjectCpid: string;
  /** CC-5: a surgical episode must carry a resolvable PCT anchor. */
  journeyId?: string | null;
  encounterId?: string | null;
  operativeIndication: string;
  nonOperativeOptionsConsidered?: string | null;
  conditionDisplay?: string | null;
  diagnosticCertainty?: string | null;
  evidence?: string | null;
  specialty?: string | null;
}

/** S2 view — free-form map from surgery-service; the reviewed-flag/ref pairs are CHECK-enforced server-side. */
export interface SurgicalAssessment {
  id?: string;
  surgicalEpisodeId?: string;
  presentingProblem?: string | null;
  symptomTimeline?: string | null;
  woundHealingHistory?: string | null;
  bleedingThrombosisHistory?: string | null;
  infectionHistory?: string | null;
  anaestheticHistoryNotes?: string | null;
  nutritionAssessment?: string | null;
  frailtyNotes?: string | null;
  socialSupportNotes?: string | null;
  transportPlan?: string | null;
  workLivelihoodImpact?: string | null;
  patientGoals?: string | null;
  examinationFindings?: string | null;
  differentialDiagnosisNotes?: string | null;
  surgicalRiskAssessment?: string | null;
  previousSurgeryReviewed?: boolean;
  previousSurgeryNotes?: string | null;
  allergiesReviewed?: boolean;
  allergyReviewNotes?: string | null;
  medicationsReviewed?: boolean;
  medicationReconciliationNotes?: string | null;
  functionalStatusReviewed?: boolean;
  functionalAssessmentRef?: string | null;
  pregnancyStatusConfirmed?: boolean;
  pregnancyEpisodeRef?: string | null;
  imagingReviewed?: boolean;
  imagingRef?: string | null;
  pathologyReviewed?: boolean;
  pathologyRef?: string | null;
  assessedBy?: string | null;
  assessedAt?: string | null;
}

export type SurgicalFinalDecision = "PROCEED" | "DO_NOT_PROCEED" | "DEFER";

/** V012 — was this decided by one clinician, or by a multidisciplinary team? */
export type SurgicalDecisionForum = "INDIVIDUAL" | "MDT";

/**
 * Which of PCT's two MDT systems of record the referenced board decision lives in. Surgery
 * references them; it deliberately does not keep a third copy. That PCT holds two at all is an
 * inherited defect recorded in the programme lease, not something this surface can resolve.
 */
export type MdtDecisionSource = "PCT_MDT_DECISION" | "PCT_MDT_CASE_ITEM";

/** S3 view — final-decision/decidedBy/decidedAt travel as an all-or-nothing trio (CHECK-enforced). */
export interface SurgicalDecision {
  id?: string;
  surgicalEpisodeId?: string;
  naturalHistory?: string | null;
  expectedBenefit?: string | null;
  /** Risks the SURGEON weighed — deliberately distinct from mvumo's risks-explained-to-patient. */
  materialRisksConsidered?: string | null;
  anaestheticImplications?: string | null;
  bloodImplications?: string | null;
  functionalImplications?: string | null;
  fertilityImplications?: string | null;
  stomaPossibility?: boolean | null;
  stomaPossibilityNotes?: string | null;
  implantPossibility?: boolean | null;
  implantPossibilityNotes?: string | null;
  rehabilitationExpectation?: string | null;
  financialAccessImplications?: string | null;
  patientPreference?: string | null;
  finalDecision?: SurgicalFinalDecision | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  /** V012 — MDT forum. `mdtDecisionRef` points into PCT; surgery keeps no board record of its own. */
  decisionForum?: SurgicalDecisionForum | null;
  mdtDecisionRef?: string | null;
  mdtDecisionSource?: MdtDecisionSource | null;
  mdtDecisionVerifiedAt?: string | null;
  /**
   * Whether surgery could actually confirm the referenced board decision exists in PCT. False
   * means recorded-but-unconfirmed (PCT was unreachable at the time), NOT "the board did not
   * decide this" — a distinction the UI must keep visible rather than showing a bare tick.
   */
  mdtDecisionVerified?: boolean;
}

/** One specialty on a shared surgical case (V011). */
export interface EpisodeSpecialty {
  id: string;
  specialty: string;
  role: "LEAD" | "SHARED";
  contribution: string | null;
  addedBy: string;
  addedAt: string;
}

/** True when a query error is the 404 "not recorded yet" gap rather than a real failure. */
export function isNotRecorded(error: unknown): boolean {
  return !!error && typeof error === "object" && (error as { status?: number }).status === 404;
}

/**
 * Episodes for a patient. Consumer contract: render `isError` as "could not read the surgical
 * record" (never an empty list); only a resolved `[]` means the patient genuinely has no
 * surgical episodes.
 */
export function useSurgicalEpisodes(subjectCpid: string | null) {
  return useQuery<SurgicalEpisode[]>({
    queryKey: ["surgery", "episodes", subjectCpid],
    queryFn: () =>
      apiClient.get<SurgicalEpisode[]>(
        `/internal/v1/surgery/episodes?subjectCpid=${encodeURIComponent(subjectCpid ?? "")}`,
      ),
    enabled: !!subjectCpid,
  });
}

export function useSurgicalEpisode(episodeId: string | null) {
  return useQuery<SurgicalEpisode>({
    queryKey: ["surgery", "episode", episodeId],
    queryFn: () => apiClient.get<SurgicalEpisode>(`/internal/v1/surgery/episodes/${episodeId}`),
    enabled: !!episodeId,
  });
}

export function useOpenSurgicalEpisode() {
  const queryClient = useQueryClient();
  return useMutation<SurgicalEpisode, unknown, OpenEpisodePayload>({
    mutationFn: (payload) =>
      apiClient.post<SurgicalEpisode>("/internal/v1/surgery/episodes", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "episodes"] });
    },
  });
}

export function useTransitionSurgicalEpisode(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<SurgicalEpisode, unknown, { status: SurgicalEpisodeStatus }>({
    mutationFn: (payload) =>
      apiClient.post<SurgicalEpisode>(
        `/internal/v1/surgery/episodes/${episodeId}/transition`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}

export function useLinkProcedureEpisode(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<SurgicalEpisode, unknown, { procedureEpisodeRef: string }>({
    mutationFn: (payload) =>
      apiClient.post<SurgicalEpisode>(
        `/internal/v1/surgery/episodes/${episodeId}/link-procedure-episode`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}

/**
 * S2 assessment read. 404 (`isNotRecorded(error)`) means "no assessment recorded yet" — the
 * page must render that as the quieter honest-gap copy, and every OTHER error as a failure.
 * `retry: false` because retrying a 404 three times only delays the honest answer.
 */
export function useSurgicalAssessment(episodeId: string | null) {
  return useQuery<SurgicalAssessment>({
    queryKey: ["surgery", "assessment", episodeId],
    queryFn: () =>
      apiClient.get<SurgicalAssessment>(`/internal/v1/surgery/episodes/${episodeId}/assessment`),
    enabled: !!episodeId,
    retry: false,
  });
}

export function useRecordSurgicalAssessment(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<SurgicalAssessment, unknown, Partial<SurgicalAssessment>>({
    mutationFn: (payload) =>
      apiClient.put<SurgicalAssessment>(
        `/internal/v1/surgery/episodes/${episodeId}/assessment`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "assessment", episodeId] });
    },
  });
}

/** S3 decision read — same 404-as-honest-gap contract as the assessment hook. */
export function useSurgicalDecision(episodeId: string | null) {
  return useQuery<SurgicalDecision>({
    queryKey: ["surgery", "decision", episodeId],
    queryFn: () =>
      apiClient.get<SurgicalDecision>(`/internal/v1/surgery/episodes/${episodeId}/decision`),
    enabled: !!episodeId,
    retry: false,
  });
}

export function useRecordSurgicalDecision(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<SurgicalDecision, unknown, Partial<SurgicalDecision>>({
    mutationFn: (payload) =>
      apiClient.put<SurgicalDecision>(
        `/internal/v1/surgery/episodes/${episodeId}/decision`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "decision", episodeId] });
    },
  });
}

/**
 * V010 reopen for a return to theatre. Distinct from `useTransitionSurgicalEpisode`: the reason
 * is mandatory and the server stamps who reopened and when. A plain transition to REOPENED is
 * refused server-side precisely so this audit cannot be sidestepped.
 */
export function useReopenSurgicalEpisode(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    SurgicalEpisode,
    unknown,
    { reason: string; reoperationOfEpisodeId?: string | null }
  >({
    mutationFn: (payload) =>
      apiClient.post<SurgicalEpisode>(`/internal/v1/surgery/episodes/${episodeId}/reopen`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}

/**
 * V011 — every specialty on the case, lead and shared. Same contract as the episode list: an
 * error is "could not read which teams are on this case", never a rendered empty roster. On a
 * theatre surface that difference decides whether a second team is expected in the room.
 */
export function useEpisodeSpecialties(episodeId: string | null) {
  return useQuery<EpisodeSpecialty[]>({
    queryKey: ["surgery", "specialties", episodeId],
    queryFn: () =>
      apiClient.get<EpisodeSpecialty[]>(`/internal/v1/surgery/episodes/${episodeId}/specialties`),
    enabled: !!episodeId,
  });
}

export function useAddEpisodeSpecialty(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    EpisodeSpecialty,
    unknown,
    { specialty: string; role?: "LEAD" | "SHARED"; contribution?: string | null }
  >({
    mutationFn: (payload) =>
      apiClient.post<EpisodeSpecialty>(
        `/internal/v1/surgery/episodes/${episodeId}/specialties`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}

/** Handing the lead over also moves the episode's own lead-specialty column, server-side. */
export function useTransferEpisodeLead(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<EpisodeSpecialty[], unknown, { specialty: string; contribution?: string | null }>({
    mutationFn: (payload) =>
      apiClient.post<EpisodeSpecialty[]>(
        `/internal/v1/surgery/episodes/${episodeId}/specialties/lead`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}

/**
 * The specialty travels as a query parameter, never a path segment. As a segment it would
 * become the ext_authz derived resource type and no policy row could match, making the route
 * permanently unreachable — see tshepo-authz V303's header.
 */
export function useRemoveEpisodeSpecialty(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<EpisodeSpecialty[], unknown, { specialty: string }>({
    mutationFn: ({ specialty }) =>
      apiClient.delete<EpisodeSpecialty[]>(
        `/internal/v1/surgery/episodes/${episodeId}/specialties?specialty=${encodeURIComponent(specialty)}`,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery"] });
    },
  });
}
