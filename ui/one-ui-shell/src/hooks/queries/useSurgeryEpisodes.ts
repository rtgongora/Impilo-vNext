/**
 * Experience UI — surgical episode (S1) + general surgical assessment (S2) + surgical
 * decision-making record (S3) + course-of-care (prehab / complications / longitudinal
 * objects / follow-up / waitlist revalidation) query hooks.
 *
 * Backed by the experience-bff surgery proxy (`/internal/v1/surgery/episodes/**`), which
 * forwards verbatim to surgery-service (the SoR for these rows — unlike procedures-service
 * this is a store, so mutation hooks exist here).
 *
 * EMPTY VS UNKNOWN VS UNAVAILABLE. `apiClient` throws on a non-2xx response, so a downstream
 * failure surfaces as `isError`/`error` on the query, never as a resolved empty result. One
 * nuance this file owns: assessment/decision/followup GETs return 404 when the record has
 * simply not been written yet — an honest gap, not a failure. `isNotRecorded` distinguishes
 * that from a real fetch failure so a page can render "not recorded yet" for 404 and a
 * genuine error banner for everything else. A consumer that collapses those states (or falls
 * through `?? []` on error) reintroduces the "no conditions for every patient" bug this
 * estate has already shipped once.
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

// ── Course-of-care (Wave A / SB-1 + SB-2) ─────────────────────────────────────

export type PrehabDomain =
  | "ANAEMIA"
  | "NUTRITION"
  | "GLYCAEMIC_CONTROL"
  | "SMOKING_CESSATION"
  | "ALCOHOL_REDUCTION"
  | "VTE_PLAN"
  | "ANTIBIOTIC_PROPHYLAXIS_PLAN"
  | "BLOOD_PREPARATION"
  | "CARDIORESPIRATORY_FITNESS"
  | "PHYSIOTHERAPY"
  | "PSYCHOLOGICAL_PREPARATION"
  | "MEDICATION_OPTIMISATION"
  | "DENTAL"
  | "WEIGHT_MANAGEMENT"
  | "FRAILTY_INTERVENTION"
  | "SOCIAL_DISCHARGE_PREPARATION";

export type PrehabStatus = "PLANNED" | "IN_PROGRESS" | "COMPLETED" | "NOT_REQUIRED" | "DECLINED";

export interface PrehabItem {
  id: string;
  surgicalEpisodeId: string;
  domain: PrehabDomain;
  status?: PrehabStatus | null;
  target?: string | null;
  notes?: string | null;
  registryRef?: string | null;
  plannedBy?: string | null;
}

export interface PrehabItemRequest {
  domain: PrehabDomain;
  status?: PrehabStatus | null;
  target?: string | null;
  notes?: string | null;
  registryRef?: string | null;
}

export type ComplicationType =
  | "SURGICAL_SITE_INFECTION"
  | "BLEEDING"
  | "ANASTOMOTIC_LEAK"
  | "DEEP_VEIN_THROMBOSIS"
  | "PULMONARY_EMBOLISM"
  | "WOUND_DEHISCENCE"
  | "ORGAN_INJURY"
  | "NERVE_INJURY"
  | "ANAESTHESIA_COMPLICATION"
  | "TRANSFUSION_REACTION"
  | "DEVICE_MALFUNCTION"
  | "RETAINED_FOREIGN_OBJECT"
  | "WRONG_SITE_PROCEDURE"
  | "READMISSION"
  | "UNPLANNED_RETURN_TO_THEATRE"
  | "SEPSIS"
  | "RENAL_INJURY"
  | "CARDIAC_EVENT"
  | "DEATH";

export type ClavienDindoGrade = "I" | "II" | "IIIA" | "IIIB" | "IVA" | "IVB" | "V";

export interface ComplicationPathway {
  id: string;
  surgicalEpisodeId: string;
  complicationType: ComplicationType;
  recognisedBy: string;
  recognisedAt: string;
  gradeCode?: ClavienDindoGrade | null;
  gradedBy?: string | null;
  ownedBy?: string | null;
  investigation?: string | null;
  treatment?: string | null;
  disclosedAt?: string | null;
  disclosedBy?: string | null;
  disclosureNotes?: string | null;
  outcome?: string | null;
  status: "OPEN" | "CLOSED";
  closedAt?: string | null;
  closedBy?: string | null;
  pctProblemRef?: string | null;
}

export type LongitudinalObjectKind = "DRAIN" | "STOMA" | "WOUND";
export type LongitudinalObjectStatus = "ACTIVE" | "REMOVED" | "REVISED";

export interface LongitudinalObject {
  id: string;
  surgicalEpisodeId: string;
  kind: LongitudinalObjectKind;
  site: string;
  placedAt: string;
  operator?: string | null;
  indication: string;
  status: LongitudinalObjectStatus;
  removedAt?: string | null;
  removedBy?: string | null;
  removalReason?: string | null;
  revisedToObjectId?: string | null;
}

export interface SurgicalFollowup {
  id: string;
  surgicalEpisodeId: string;
  restrictions?: string | null;
  fitNoteNotes?: string | null;
  fitNoteIssuedAt?: string | null;
  fitNoteIssuedBy?: string | null;
  transportArranged: boolean;
  transportNotes?: string | null;
  futureSurgeryIntent?: string | null;
  surveillancePlanRef?: string | null;
  recordedBy?: string | null;
  recordedAt?: string | null;
}

export interface FollowupRecordRequest {
  restrictions?: string | null;
  fitNoteNotes?: string | null;
  transportArranged?: boolean;
  transportNotes?: string | null;
  futureSurgeryIntent?: string | null;
  surveillancePlanRef?: string | null;
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

export interface WaitlistRevalidationRequest {
  stillClinicallyAppropriate: boolean;
  notes?: string | null;
  nextRevalidationDue?: string | null;
}

/** List contract matches specialties: isError ≠ empty roster of optimisation items. */
export function usePrehabItems(episodeId: string | null) {
  return useQuery<PrehabItem[]>({
    queryKey: ["surgery", "prehab", episodeId],
    queryFn: () =>
      apiClient.get<PrehabItem[]>(`/internal/v1/surgery/episodes/${episodeId}/prehab`),
    enabled: !!episodeId,
  });
}

export function useRecordPrehabItem(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<PrehabItem, unknown, PrehabItemRequest>({
    mutationFn: (payload) =>
      apiClient.put<PrehabItem>(`/internal/v1/surgery/episodes/${episodeId}/prehab`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "prehab", episodeId] });
    },
  });
}

/** List contract: isError ≠ "no complications" — a failed read must not look empty. */
export function useComplicationPathways(episodeId: string | null) {
  return useQuery<ComplicationPathway[]>({
    queryKey: ["surgery", "complications", episodeId],
    queryFn: () =>
      apiClient.get<ComplicationPathway[]>(
        `/internal/v1/surgery/episodes/${episodeId}/complications`,
      ),
    enabled: !!episodeId,
  });
}

export function useRecogniseComplication(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<ComplicationPathway, unknown, { complicationType: ComplicationType }>({
    mutationFn: (payload) =>
      apiClient.post<ComplicationPathway>(
        `/internal/v1/surgery/episodes/${episodeId}/complications`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] });
    },
  });
}

export function useGradeComplication(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    ComplicationPathway,
    unknown,
    { pathwayId: string; gradeCode: ClavienDindoGrade }
  >({
    mutationFn: ({ pathwayId, gradeCode }) =>
      apiClient.post<ComplicationPathway>(
        `/internal/v1/surgery/episodes/${episodeId}/complications/${pathwayId}/grade`,
        { gradeCode },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] });
    },
  });
}

export function useUpdateComplication(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    ComplicationPathway,
    unknown,
    {
      pathwayId: string;
      ownedBy?: string | null;
      investigation?: string | null;
      treatment?: string | null;
    }
  >({
    mutationFn: ({ pathwayId, ...payload }) =>
      apiClient.put<ComplicationPathway>(
        `/internal/v1/surgery/episodes/${episodeId}/complications/${pathwayId}`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] });
    },
  });
}

export function useDiscloseComplication(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    ComplicationPathway,
    unknown,
    { pathwayId: string; disclosureNotes?: string | null }
  >({
    mutationFn: ({ pathwayId, disclosureNotes }) =>
      apiClient.post<ComplicationPathway>(
        `/internal/v1/surgery/episodes/${episodeId}/complications/${pathwayId}/disclose`,
        { disclosureNotes: disclosureNotes ?? null },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] });
    },
  });
}

export function useCloseComplication(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<ComplicationPathway, unknown, { pathwayId: string; outcome: string }>({
    mutationFn: ({ pathwayId, outcome }) =>
      apiClient.post<ComplicationPathway>(
        `/internal/v1/surgery/episodes/${episodeId}/complications/${pathwayId}/close`,
        { outcome },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] });
    },
  });
}

/** List contract: isError ≠ "no drains/stomas/wounds on this case". */
export function useLongitudinalObjects(episodeId: string | null) {
  return useQuery<LongitudinalObject[]>({
    queryKey: ["surgery", "longitudinal-objects", episodeId],
    queryFn: () =>
      apiClient.get<LongitudinalObject[]>(
        `/internal/v1/surgery/episodes/${episodeId}/longitudinal-objects`,
      ),
    enabled: !!episodeId,
  });
}

export function usePlaceLongitudinalObject(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    LongitudinalObject,
    unknown,
    {
      kind: LongitudinalObjectKind;
      site: string;
      indication: string;
      operator?: string | null;
    }
  >({
    mutationFn: (payload) =>
      apiClient.post<LongitudinalObject>(
        `/internal/v1/surgery/episodes/${episodeId}/longitudinal-objects`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "longitudinal-objects", episodeId] });
    },
  });
}

export function useRemoveLongitudinalObject(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    LongitudinalObject,
    unknown,
    { objectId: string; removalReason?: string | null }
  >({
    mutationFn: ({ objectId, removalReason }) =>
      apiClient.post<LongitudinalObject>(
        `/internal/v1/surgery/episodes/${episodeId}/longitudinal-objects/${objectId}/remove`,
        { removalReason: removalReason ?? null },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "longitudinal-objects", episodeId] });
    },
  });
}

export function useReviseLongitudinalObject(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    LongitudinalObject,
    unknown,
    {
      objectId: string;
      site?: string | null;
      indication?: string | null;
      operator?: string | null;
      removalReason?: string | null;
    }
  >({
    mutationFn: ({ objectId, ...payload }) =>
      apiClient.post<LongitudinalObject>(
        `/internal/v1/surgery/episodes/${episodeId}/longitudinal-objects/${objectId}/revise`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "longitudinal-objects", episodeId] });
    },
  });
}

/**
 * Follow-up read — same 404-as-honest-gap contract as assessment/decision. `retry: false`
 * because retrying a 404 three times only delays the honest answer.
 */
export function useSurgicalFollowup(episodeId: string | null) {
  return useQuery<SurgicalFollowup>({
    queryKey: ["surgery", "followup", episodeId],
    queryFn: () =>
      apiClient.get<SurgicalFollowup>(`/internal/v1/surgery/episodes/${episodeId}/followup`),
    enabled: !!episodeId,
    retry: false,
  });
}

export function useRecordSurgicalFollowup(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<SurgicalFollowup, unknown, FollowupRecordRequest>({
    mutationFn: (payload) =>
      apiClient.put<SurgicalFollowup>(
        `/internal/v1/surgery/episodes/${episodeId}/followup`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["surgery", "followup", episodeId] });
    },
  });
}

/** List contract: isError ≠ "never revalidated" — a failed history must not look empty. */
export function useWaitlistRevalidations(episodeId: string | null) {
  return useQuery<WaitlistRevalidation[]>({
    queryKey: ["surgery", "waitlist-revalidation", episodeId],
    queryFn: () =>
      apiClient.get<WaitlistRevalidation[]>(
        `/internal/v1/surgery/episodes/${episodeId}/waitlist-revalidation`,
      ),
    enabled: !!episodeId,
  });
}

export function useAppendWaitlistRevalidation(episodeId: string | null) {
  const queryClient = useQueryClient();
  return useMutation<WaitlistRevalidation, unknown, WaitlistRevalidationRequest>({
    mutationFn: (payload) =>
      apiClient.post<WaitlistRevalidation>(
        `/internal/v1/surgery/episodes/${episodeId}/waitlist-revalidation`,
        payload,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["surgery", "waitlist-revalidation", episodeId],
      });
    },
  });
}
