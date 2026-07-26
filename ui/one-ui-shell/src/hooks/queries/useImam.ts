import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * IMAM — treatment episodes for acute malnutrition.
 *
 * pct-service is the system of record and the clinical knowledge platform owns the protocol;
 * the BFF composes the two. These hooks map its snake_case contract into the shapes the screens
 * use, and change nothing else — no thresholds, no derived verdicts. A UI that recomputed
 * "dischargeable" would eventually disagree with the record about whether a child was cured.
 *
 * Two nullable fields are load-bearing and must stay nullable all the way to the screen:
 * `dischargeEligible` is null when the knowledge platform could not be reached, which is an
 * absence of assessment rather than a finding about the child, and `dangerSignsPresent` is null
 * when nobody looked. Coercing either to false would turn "unknown" into "fine".
 */

type Nullable<T> = T | null;

interface RawCriterion {
  code: string;
  name: string;
  met: boolean;
  detail: Nullable<string>;
}

interface RawAssessment {
  programme: Nullable<string>;
  programme_name: Nullable<string>;
  assessable: boolean;
  not_assessable_reason: Nullable<string>;
  reviews_recorded: number;
  no_review_recorded: boolean;
  days_in_programme: number;
  discharge_eligible: Nullable<boolean>;
  discharge_criteria: RawCriterion[];
  unmet_criteria: string[];
  discharge_outcome: Nullable<string>;
  discharge_destination: Nullable<string>;
  attendance_status: Nullable<string>;
  consecutive_missed_visits: Nullable<number>;
  days_since_last_contact: Nullable<number>;
  next_review_due: Nullable<string>;
  days_overdue: Nullable<number>;
  tracing_required: boolean;
  response_status: Nullable<string>;
  weight_gain_g_per_kg_per_day: Nullable<number>;
  escalation: Nullable<string>;
  escalation_action: Nullable<string>;
  recommended_sachets_per_week: Nullable<number>;
  therapeutic_food: Nullable<string>;
  actions: string[];
  content_version: Nullable<string>;
  approval_status: Nullable<string>;
  note: Nullable<string>;
}

interface RawVisit {
  imam_visit_id: string;
  imam_episode_id: string;
  patient_id: string;
  encounter_id: Nullable<string>;
  visit_number: number;
  scheduled_for: Nullable<string>;
  attended: boolean;
  visit_date: Nullable<string>;
  recorded_by: Nullable<string>;
  muac_cm: Nullable<number>;
  weight_kg: Nullable<number>;
  height_cm: Nullable<number>;
  weight_for_height_z: Nullable<number>;
  oedema: Nullable<string>;
  appetite_test: Nullable<string>;
  danger_signs_present: Nullable<boolean>;
  rutf_product_code: Nullable<string>;
  rutf_sachets_issued: Nullable<number>;
  rutf_sachets_recommended: Nullable<number>;
  clinical_note: Nullable<string>;
  next_review_due: Nullable<string>;
  discharge_eligible: Nullable<boolean>;
  attendance_status: Nullable<string>;
  response_status: Nullable<string>;
  assessment_note: Nullable<string>;
  assessment_content_version: Nullable<string>;
  created_at: Nullable<string>;
}

interface RawEpisode {
  imam_episode_id: string;
  patient_id: string;
  journey_id: Nullable<string>;
  encounter_id: Nullable<string>;
  facility_id: Nullable<string>;
  programme: string;
  status: string;
  enrolled_at: Nullable<string>;
  enrolled_by: Nullable<string>;
  enrolment_source: Nullable<string>;
  source_classification: Nullable<string>;
  admission_criterion: Nullable<string>;
  admission_age_days: Nullable<number>;
  admission_muac_cm: Nullable<number>;
  admission_weight_kg: Nullable<number>;
  admission_height_cm: Nullable<number>;
  admission_weight_for_height_z: Nullable<number>;
  admission_oedema: Nullable<string>;
  admission_appetite_test: Nullable<string>;
  admission_complications: Nullable<string>;
  content_pack_id: Nullable<string>;
  content_version: Nullable<string>;
  approval_status: Nullable<string>;
  review_interval_days: Nullable<number>;
  attendance_grace_days: Nullable<number>;
  trace_after_missed_visits: Nullable<number>;
  defaulter_missed_visits: Nullable<number>;
  last_contact_on: Nullable<string>;
  next_review_due: Nullable<string>;
  outcome: Nullable<string>;
  outcome_at: Nullable<string>;
  outcome_by: Nullable<string>;
  outcome_note: Nullable<string>;
  discharge_muac_cm: Nullable<number>;
  discharge_weight_kg: Nullable<number>;
  discharge_oedema: Nullable<string>;
  discharge_criteria_met: Nullable<boolean>;
  discharge_criteria_detail: Nullable<string>;
  discharge_override_reason: Nullable<string>;
  created_at: Nullable<string>;
  visits?: RawVisit[];
  assessment?: RawAssessment;
}

interface RawTracingRow {
  imam_episode_id: string;
  patient_id: string;
  programme: string;
  facility_id: Nullable<string>;
  enrolled_at: Nullable<string>;
  last_contact_on: Nullable<string>;
  next_review_due: Nullable<string>;
  days_since_last_contact: number;
  consecutive_missed_visits: number;
  attendance_status: string;
  admission_muac_cm: Nullable<number>;
  admission_oedema: Nullable<string>;
  never_reviewed: boolean;
}

export interface DischargeCriterion {
  code: string;
  name: string;
  met: boolean;
  detail: string | null;
}

export interface ImamAssessment {
  programme: string | null;
  programmeName: string | null;
  assessable: boolean;
  notAssessableReason: string | null;
  reviewsRecorded: number;
  noReviewRecorded: boolean;
  daysInProgramme: number;
  /** null = the knowledge platform could not be reached. Never render this as "not ready". */
  dischargeEligible: boolean | null;
  dischargeCriteria: DischargeCriterion[];
  unmetCriteria: string[];
  dischargeOutcome: string | null;
  dischargeDestination: string | null;
  attendanceStatus: string | null;
  consecutiveMissedVisits: number | null;
  daysSinceLastContact: number | null;
  nextReviewDue: string | null;
  daysOverdue: number | null;
  tracingRequired: boolean;
  responseStatus: string | null;
  weightGainGPerKgPerDay: number | null;
  escalation: string | null;
  escalationAction: string | null;
  recommendedSachetsPerWeek: number | null;
  therapeuticFood: string | null;
  actions: string[];
  contentVersion: string | null;
  approvalStatus: string | null;
  note: string | null;
}

export interface ImamVisit {
  id: string;
  episodeId: string;
  visitNumber: number;
  scheduledFor: string | null;
  attended: boolean;
  visitDate: string | null;
  recordedBy: string | null;
  muacCm: number | null;
  weightKg: number | null;
  heightCm: number | null;
  weightForHeightZ: number | null;
  oedema: string | null;
  appetiteTest: string | null;
  /** null = danger signs were not assessed, which is not the same as none. */
  dangerSignsPresent: boolean | null;
  rutfProductCode: string | null;
  rutfSachetsIssued: number | null;
  rutfSachetsRecommended: number | null;
  clinicalNote: string | null;
  nextReviewDue: string | null;
  dischargeEligible: boolean | null;
  attendanceStatus: string | null;
  responseStatus: string | null;
  assessmentNote: string | null;
  assessmentContentVersion: string | null;
}

export interface ImamEpisode {
  id: string;
  patientId: string;
  journeyId: string | null;
  encounterId: string | null;
  facilityId: string | null;
  programme: string;
  status: string;
  enrolledAt: string | null;
  enrolledBy: string | null;
  enrolmentSource: string | null;
  sourceClassification: string | null;
  admissionCriterion: string | null;
  admissionAgeDays: number | null;
  admissionMuacCm: number | null;
  admissionWeightKg: number | null;
  admissionHeightCm: number | null;
  admissionWeightForHeightZ: number | null;
  admissionOedema: string | null;
  admissionAppetiteTest: string | null;
  admissionComplications: string | null;
  contentPackId: string | null;
  contentVersion: string | null;
  approvalStatus: string | null;
  reviewIntervalDays: number | null;
  traceAfterMissedVisits: number | null;
  defaulterMissedVisits: number | null;
  lastContactOn: string | null;
  nextReviewDue: string | null;
  outcome: string | null;
  outcomeAt: string | null;
  outcomeBy: string | null;
  outcomeNote: string | null;
  dischargeMuacCm: number | null;
  dischargeWeightKg: number | null;
  dischargeOedema: string | null;
  dischargeCriteriaMet: boolean | null;
  dischargeCriteriaDetail: string | null;
  dischargeOverrideReason: string | null;
  visits: ImamVisit[];
  assessment: ImamAssessment | null;
}

export interface ImamTracingRow {
  episodeId: string;
  patientId: string;
  programme: string;
  facilityId: string | null;
  enrolledAt: string | null;
  lastContactOn: string | null;
  nextReviewDue: string | null;
  daysSinceLastContact: number;
  consecutiveMissedVisits: number;
  attendanceStatus: string;
  admissionMuacCm: number | null;
  admissionOedema: string | null;
  /** Enrolled and never seen — the most urgent row, not the quietest. */
  neverReviewed: boolean;
}

function mapAssessment(raw: RawAssessment | undefined): ImamAssessment | null {
  if (!raw) return null;
  return {
    programme: raw.programme ?? null,
    programmeName: raw.programme_name ?? null,
    assessable: raw.assessable ?? false,
    notAssessableReason: raw.not_assessable_reason ?? null,
    reviewsRecorded: raw.reviews_recorded ?? 0,
    noReviewRecorded: raw.no_review_recorded ?? false,
    daysInProgramme: raw.days_in_programme ?? 0,
    dischargeEligible: raw.discharge_eligible ?? null,
    dischargeCriteria: (raw.discharge_criteria ?? []).map((c) => ({
      code: c.code,
      name: c.name,
      met: c.met,
      detail: c.detail ?? null,
    })),
    unmetCriteria: raw.unmet_criteria ?? [],
    dischargeOutcome: raw.discharge_outcome ?? null,
    dischargeDestination: raw.discharge_destination ?? null,
    attendanceStatus: raw.attendance_status ?? null,
    consecutiveMissedVisits: raw.consecutive_missed_visits ?? null,
    daysSinceLastContact: raw.days_since_last_contact ?? null,
    nextReviewDue: raw.next_review_due ?? null,
    daysOverdue: raw.days_overdue ?? null,
    tracingRequired: raw.tracing_required ?? false,
    responseStatus: raw.response_status ?? null,
    weightGainGPerKgPerDay: raw.weight_gain_g_per_kg_per_day ?? null,
    escalation: raw.escalation ?? null,
    escalationAction: raw.escalation_action ?? null,
    recommendedSachetsPerWeek: raw.recommended_sachets_per_week ?? null,
    therapeuticFood: raw.therapeutic_food ?? null,
    actions: raw.actions ?? [],
    contentVersion: raw.content_version ?? null,
    approvalStatus: raw.approval_status ?? null,
    note: raw.note ?? null,
  };
}

function mapVisit(raw: RawVisit): ImamVisit {
  return {
    id: raw.imam_visit_id,
    episodeId: raw.imam_episode_id,
    visitNumber: raw.visit_number,
    scheduledFor: raw.scheduled_for ?? null,
    attended: raw.attended,
    visitDate: raw.visit_date ?? null,
    recordedBy: raw.recorded_by ?? null,
    muacCm: raw.muac_cm ?? null,
    weightKg: raw.weight_kg ?? null,
    heightCm: raw.height_cm ?? null,
    weightForHeightZ: raw.weight_for_height_z ?? null,
    oedema: raw.oedema ?? null,
    appetiteTest: raw.appetite_test ?? null,
    dangerSignsPresent: raw.danger_signs_present ?? null,
    rutfProductCode: raw.rutf_product_code ?? null,
    rutfSachetsIssued: raw.rutf_sachets_issued ?? null,
    rutfSachetsRecommended: raw.rutf_sachets_recommended ?? null,
    clinicalNote: raw.clinical_note ?? null,
    nextReviewDue: raw.next_review_due ?? null,
    dischargeEligible: raw.discharge_eligible ?? null,
    attendanceStatus: raw.attendance_status ?? null,
    responseStatus: raw.response_status ?? null,
    assessmentNote: raw.assessment_note ?? null,
    assessmentContentVersion: raw.assessment_content_version ?? null,
  };
}

function mapEpisode(raw: RawEpisode): ImamEpisode {
  return {
    id: raw.imam_episode_id,
    patientId: raw.patient_id,
    journeyId: raw.journey_id ?? null,
    encounterId: raw.encounter_id ?? null,
    facilityId: raw.facility_id ?? null,
    programme: raw.programme,
    status: raw.status,
    enrolledAt: raw.enrolled_at ?? null,
    enrolledBy: raw.enrolled_by ?? null,
    enrolmentSource: raw.enrolment_source ?? null,
    sourceClassification: raw.source_classification ?? null,
    admissionCriterion: raw.admission_criterion ?? null,
    admissionAgeDays: raw.admission_age_days ?? null,
    admissionMuacCm: raw.admission_muac_cm ?? null,
    admissionWeightKg: raw.admission_weight_kg ?? null,
    admissionHeightCm: raw.admission_height_cm ?? null,
    admissionWeightForHeightZ: raw.admission_weight_for_height_z ?? null,
    admissionOedema: raw.admission_oedema ?? null,
    admissionAppetiteTest: raw.admission_appetite_test ?? null,
    admissionComplications: raw.admission_complications ?? null,
    contentPackId: raw.content_pack_id ?? null,
    contentVersion: raw.content_version ?? null,
    approvalStatus: raw.approval_status ?? null,
    reviewIntervalDays: raw.review_interval_days ?? null,
    traceAfterMissedVisits: raw.trace_after_missed_visits ?? null,
    defaulterMissedVisits: raw.defaulter_missed_visits ?? null,
    lastContactOn: raw.last_contact_on ?? null,
    nextReviewDue: raw.next_review_due ?? null,
    outcome: raw.outcome ?? null,
    outcomeAt: raw.outcome_at ?? null,
    outcomeBy: raw.outcome_by ?? null,
    outcomeNote: raw.outcome_note ?? null,
    dischargeMuacCm: raw.discharge_muac_cm ?? null,
    dischargeWeightKg: raw.discharge_weight_kg ?? null,
    dischargeOedema: raw.discharge_oedema ?? null,
    dischargeCriteriaMet: raw.discharge_criteria_met ?? null,
    dischargeCriteriaDetail: raw.discharge_criteria_detail ?? null,
    dischargeOverrideReason: raw.discharge_override_reason ?? null,
    visits: (raw.visits ?? []).map(mapVisit),
    assessment: mapAssessment(raw.assessment),
  };
}

function mapTracingRow(raw: RawTracingRow): ImamTracingRow {
  return {
    episodeId: raw.imam_episode_id,
    patientId: raw.patient_id,
    programme: raw.programme,
    facilityId: raw.facility_id ?? null,
    enrolledAt: raw.enrolled_at ?? null,
    lastContactOn: raw.last_contact_on ?? null,
    nextReviewDue: raw.next_review_due ?? null,
    daysSinceLastContact: raw.days_since_last_contact,
    consecutiveMissedVisits: raw.consecutive_missed_visits,
    attendanceStatus: raw.attendance_status,
    admissionMuacCm: raw.admission_muac_cm ?? null,
    admissionOedema: raw.admission_oedema ?? null,
    neverReviewed: raw.never_reviewed,
  };
}

export const imamKeys = {
  episodes: (patientId: string) => ["imam", "episodes", { patientId }] as const,
  episode: (episodeId: string) => ["imam", "episode", { episodeId }] as const,
  tracing: (facilityId: string | null) => ["imam", "tracing", { facilityId }] as const,
};

export function useImamEpisodes(patientId: string) {
  return useQuery<ImamEpisode[]>({
    queryKey: imamKeys.episodes(patientId),
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<RawEpisode[]>>(
        `/internal/v1/imam/episodes?patient_id=${encodeURIComponent(patientId)}`,
      );
      return (response.data ?? []).map(mapEpisode);
    },
    enabled: !!patientId,
  });
}

export function useImamEpisode(episodeId: string | null) {
  return useQuery<ImamEpisode>({
    queryKey: imamKeys.episode(episodeId ?? ""),
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<RawEpisode>>(
        `/internal/v1/imam/episodes/${encodeURIComponent(episodeId!)}`,
      );
      return mapEpisode(response.data);
    },
    enabled: !!episodeId,
  });
}

export function useImamTracingQueue(facilityId: string | null) {
  return useQuery<ImamTracingRow[]>({
    queryKey: imamKeys.tracing(facilityId),
    queryFn: async () => {
      const query = facilityId ? `?facility_id=${encodeURIComponent(facilityId)}` : "";
      const response = await apiClient.get<ApiResponse<RawTracingRow[]>>(
        `/internal/v1/imam/tracing-queue${query}`,
      );
      return (response.data ?? []).map(mapTracingRow);
    },
  });
}

export interface EnrolImamPayload {
  patientId: string;
  journeyId?: string | null;
  encounterId?: string | null;
  facilityId?: string | null;
  /** The IMNCI acute-malnutrition classification, when enrolling straight off an assessment. */
  sourceClassification?: string | null;
  /** Only when the clinician is choosing the programme themselves. */
  programme?: string | null;
  admissionAgeDays?: number | null;
  admissionMuacCm?: number | null;
  admissionWeightKg?: number | null;
  admissionHeightCm?: number | null;
  admissionOedema?: string | null;
  admissionAppetiteTest?: string | null;
  medicalComplication?: boolean | null;
  admissionComplications?: string | null;
}

export function useEnrolImam() {
  const queryClient = useQueryClient();
  return useMutation<ImamEpisode, unknown, EnrolImamPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<ApiResponse<RawEpisode>>("/internal/v1/imam/episodes", {
        patient_id: payload.patientId,
        journey_id: payload.journeyId ?? null,
        encounter_id: payload.encounterId ?? null,
        facility_id: payload.facilityId ?? null,
        source_classification: payload.sourceClassification ?? null,
        programme: payload.programme ?? null,
        admission_age_days: payload.admissionAgeDays ?? null,
        admission_muac_cm: payload.admissionMuacCm ?? null,
        admission_weight_kg: payload.admissionWeightKg ?? null,
        admission_height_cm: payload.admissionHeightCm ?? null,
        admission_oedema: payload.admissionOedema ?? null,
        admission_appetite_test: payload.admissionAppetiteTest ?? null,
        medical_complication: payload.medicalComplication ?? null,
        admission_complications: payload.admissionComplications ?? null,
      });
      return mapEpisode(response.data);
    },
    onSuccess: (_episode, payload) => {
      queryClient.invalidateQueries({ queryKey: imamKeys.episodes(payload.patientId) });
    },
  });
}

export interface RecordImamVisitPayload {
  episodeId: string;
  patientId: string;
  attended: boolean;
  visitDate?: string | null;
  scheduledFor?: string | null;
  encounterId?: string | null;
  muacCm?: number | null;
  weightKg?: number | null;
  heightCm?: number | null;
  oedema?: string | null;
  appetiteTest?: string | null;
  dangerSignsPresent?: boolean | null;
  rutfSachetsIssued?: number | null;
  clinicalNote?: string | null;
}

export function useRecordImamVisit() {
  const queryClient = useQueryClient();
  return useMutation<ImamVisit, unknown, RecordImamVisitPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<ApiResponse<RawVisit>>(
        `/internal/v1/imam/episodes/${encodeURIComponent(payload.episodeId)}/visits`,
        {
          attended: payload.attended,
          visit_date: payload.attended ? (payload.visitDate ?? new Date().toISOString()) : null,
          scheduled_for: payload.scheduledFor ?? null,
          encounter_id: payload.encounterId ?? null,
          muac_cm: payload.muacCm ?? null,
          weight_kg: payload.weightKg ?? null,
          height_cm: payload.heightCm ?? null,
          oedema: payload.oedema ?? null,
          appetite_test: payload.appetiteTest ?? null,
          danger_signs_present: payload.dangerSignsPresent ?? null,
          rutf_sachets_issued: payload.rutfSachetsIssued ?? null,
          clinical_note: payload.clinicalNote ?? null,
        },
      );
      return mapVisit(response.data);
    },
    onSuccess: (_visit, payload) => {
      queryClient.invalidateQueries({ queryKey: imamKeys.episode(payload.episodeId) });
      queryClient.invalidateQueries({ queryKey: imamKeys.episodes(payload.patientId) });
      queryClient.invalidateQueries({ queryKey: ["imam", "tracing"] });
    },
  });
}

export interface CloseImamEpisodePayload {
  episodeId: string;
  patientId: string;
  outcome: string;
  outcomeAt?: string | null;
  outcomeNote?: string | null;
  dischargeOverrideReason?: string | null;
}

export function useCloseImamEpisode() {
  const queryClient = useQueryClient();
  return useMutation<ImamEpisode, unknown, CloseImamEpisodePayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<ApiResponse<RawEpisode>>(
        `/internal/v1/imam/episodes/${encodeURIComponent(payload.episodeId)}/outcome`,
        {
          outcome: payload.outcome,
          outcome_at: payload.outcomeAt ?? new Date().toISOString(),
          outcome_note: payload.outcomeNote ?? null,
          discharge_override_reason: payload.dischargeOverrideReason ?? null,
        },
      );
      return mapEpisode(response.data);
    },
    onSuccess: (_episode, payload) => {
      queryClient.invalidateQueries({ queryKey: imamKeys.episode(payload.episodeId) });
      queryClient.invalidateQueries({ queryKey: imamKeys.episodes(payload.patientId) });
      queryClient.invalidateQueries({ queryKey: ["imam", "tracing"] });
    },
  });
}
