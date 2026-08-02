import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * The four paediatric decision engines, via the experience BFF.
 *
 * The clinical knowledge platform owns every judgement here. These hooks map the snake_case
 * contract into the shapes the screens use and do nothing else — no thresholds, no derived
 * verdicts, no defaults. A UI that recomputed a classification or decided for itself whether a
 * dose was giveable would eventually disagree with the engine about the same child, and the
 * engine is the one with the governed content behind it.
 *
 * **Every nullable field below is load-bearing and must stay nullable all the way to the screen.**
 * These engines exist to distinguish an unanswered question from a reassuring answer, and that
 * distinction only survives if nothing between here and the pixel coerces it:
 *
 * - `status: "INDETERMINATE"` with `missingInputs` — the chart could not be resolved and names
 *   what it needs. Rendering this as the green row is the one failure that would make the whole
 *   pack worthless.
 * - `provisional` — a severer row could not be excluded, so the classification shown is the best
 *   available and not a conclusion.
 * - `actionableToday: false` on a `BLOCKED_BY_INTERVAL` or `AGED_OUT` dose — a dose that would not
 *   immunise the child. Presenting it as giveable wastes a dose and records a child as protected
 *   who is not.
 * - `interpretationBlocked` — the measurement is in question, and the engine has *already*
 *   suppressed the clinical signals. The screen must suppress them too rather than reaching past
 *   it for a signal list that is empty for a reason.
 * - `incomplete` with `outstandingAssessments` — a danger-sign screen built on questions nobody
 *   asked, which is never an all-clear.
 *
 * A failed call is an error, never an empty result: the BFF returns 502 with no `data` key
 * precisely so that a consumer cannot mistake an unreachable engine for a well child. Consult
 * `isError` before rendering anything reassuring.
 */

type Nullable<T> = T | null;

// ---------- IMNCI ----------

/** OK = the table resolved. INDETERMINATE = it could not, and says what it needs. */
export type ImnciStatus = "OK" | "INDETERMINATE" | "NOT_APPLICABLE" | string;

interface RawClassification {
  table_id: string;
  axis: Nullable<string>;
  status: ImnciStatus;
  classification: Nullable<string>;
  classification_name: Nullable<string>;
  colour: Nullable<string>;
  provisional: boolean;
  actionable: boolean;
  unresolved_tiers: Nullable<string[]>;
  missing_inputs: Nullable<string[]>;
  waived_criteria: Nullable<string[]>;
  action: Nullable<string>;
  treatments: Nullable<string[]>;
  referral_required: boolean;
  urgent_referral: boolean;
  rationale: Nullable<string>;
}

interface RawImnci {
  age_days: Nullable<number>;
  pathway: Nullable<string>;
  applicable: boolean;
  classifications: RawClassification[];
  urgent_referral_required: boolean;
  referral_required: boolean;
  incomplete: boolean;
  outstanding_assessments: Nullable<string[]>;
  unavailable_criteria: Nullable<string[]>;
  treatment_plan: Nullable<string[]>;
  disposition: Nullable<string>;
  content_version: Nullable<string>;
  content_source: Nullable<string>;
  approval_status: Nullable<string>;
  note: Nullable<string>;
}

export interface ImnciClassification {
  tableId: string;
  axis: string | null;
  status: ImnciStatus;
  classification: string | null;
  classificationName: string | null;
  /** pink | yellow | green. Null when the row did not resolve — never defaulted to green. */
  colour: string | null;
  /** A severer row could not be excluded, so this is the best available and not a conclusion. */
  provisional: boolean;
  actionable: boolean;
  unresolvedTiers: string[];
  /** The observations that would resolve an INDETERMINATE row. Naming them is the point. */
  missingInputs: string[];
  /** Criteria the engine waived because the equipment to test them may not exist here. */
  waivedCriteria: string[];
  action: string | null;
  treatments: string[];
  referralRequired: boolean;
  urgentReferral: boolean;
  rationale: string | null;
}

export interface ImnciAssessment {
  ageDays: number | null;
  /** Which chart was run. A 10-day-old must be visibly on the young-infant chart. */
  pathway: string | null;
  applicable: boolean;
  classifications: ImnciClassification[];
  urgentReferralRequired: boolean;
  referralRequired: boolean;
  /** True when something was not assessed. Never render a complete-looking result over this. */
  incomplete: boolean;
  outstandingAssessments: string[];
  unavailableCriteria: string[];
  treatmentPlan: string[];
  disposition: string | null;
  contentVersion: string | null;
  contentSource: string | null;
  approvalStatus: string | null;
  note: string | null;
}

function mapClassification(raw: RawClassification): ImnciClassification {
  return {
    tableId: raw.table_id,
    axis: raw.axis ?? null,
    status: raw.status,
    classification: raw.classification ?? null,
    classificationName: raw.classification_name ?? null,
    colour: raw.colour ?? null,
    provisional: raw.provisional ?? false,
    actionable: raw.actionable ?? false,
    unresolvedTiers: raw.unresolved_tiers ?? [],
    missingInputs: raw.missing_inputs ?? [],
    waivedCriteria: raw.waived_criteria ?? [],
    action: raw.action ?? null,
    treatments: raw.treatments ?? [],
    referralRequired: raw.referral_required ?? false,
    urgentReferral: raw.urgent_referral ?? false,
    rationale: raw.rationale ?? null,
  };
}

function mapImnci(raw: RawImnci): ImnciAssessment {
  return {
    ageDays: raw.age_days ?? null,
    pathway: raw.pathway ?? null,
    applicable: raw.applicable ?? false,
    classifications: (raw.classifications ?? []).map(mapClassification),
    urgentReferralRequired: raw.urgent_referral_required ?? false,
    referralRequired: raw.referral_required ?? false,
    incomplete: raw.incomplete ?? false,
    outstandingAssessments: raw.outstanding_assessments ?? [],
    unavailableCriteria: raw.unavailable_criteria ?? [],
    treatmentPlan: raw.treatment_plan ?? [],
    disposition: raw.disposition ?? null,
    contentVersion: raw.content_version ?? null,
    contentSource: raw.content_source ?? null,
    approvalStatus: raw.approval_status ?? null,
    note: raw.note ?? null,
  };
}

// ---------- EPI forecast ----------

export type DoseStatus =
  | "GIVEN"
  | "INVALID_REPEAT_REQUIRED"
  | "GIVEN_UNVERIFIED"
  | "DUE"
  | "OVERDUE"
  | "NOT_YET_DUE"
  | "BLOCKED_BY_INTERVAL"
  | "AWAITING_PREVIOUS_DOSE"
  | "AGED_OUT"
  | "CONTRAINDICATED"
  | "NOT_APPLICABLE"
  | string;

interface RawForecastRow {
  series: string;
  antigen: Nullable<string>;
  dose_number: Nullable<number>;
  status: DoseStatus;
  actionable_today: boolean;
  earliest_date: Nullable<string>;
  recommended_date: Nullable<string>;
  overdue_after: Nullable<string>;
  latest_useful_date: Nullable<string>;
  days_overdue: Nullable<number>;
  given_on: Nullable<string>;
  rationale: Nullable<string>;
}

interface RawForecast {
  date_of_birth: Nullable<string>;
  as_of: Nullable<string>;
  age_days: Nullable<number>;
  doses: RawForecastRow[];
  due_now: Nullable<string[]>;
  any_overdue: boolean;
  provisional: boolean;
  provisional_reasons: Nullable<string[]>;
  schedule_id: Nullable<string>;
  schedule_version: Nullable<string>;
  approval_status: Nullable<string>;
  note: Nullable<string>;
}

export interface ForecastDose {
  series: string;
  antigen: string | null;
  doseNumber: number | null;
  status: DoseStatus;
  /** The engine's own verdict on whether giving this today would immunise the child. */
  actionableToday: boolean;
  /** For BLOCKED_BY_INTERVAL this is the date the gate opens. */
  earliestDate: string | null;
  recommendedDate: string | null;
  overdueAfter: string | null;
  /** For AGED_OUT this is the date the window closed. */
  latestUsefulDate: string | null;
  daysOverdue: number | null;
  givenOn: string | null;
  /** The engine's stated reason. For AGED_OUT this carries the safety reason. */
  rationale: string | null;
}

export interface ImmunisationForecast {
  dateOfBirth: string | null;
  asOf: string | null;
  ageDays: number | null;
  doses: ForecastDose[];
  dueNow: string[];
  anyOverdue: boolean;
  provisional: boolean;
  provisionalReasons: string[];
  scheduleId: string | null;
  scheduleVersion: string | null;
  approvalStatus: string | null;
  note: string | null;
}

function mapForecastDose(raw: RawForecastRow): ForecastDose {
  // The BFF stringifies nulls as the literal "null" in a couple of date fields because the engine
  // serialises with String.valueOf. Treat that as absent rather than rendering the word.
  const date = (v: Nullable<string>) => (v && v !== "null" ? v : null);
  return {
    series: raw.series,
    antigen: raw.antigen ?? null,
    doseNumber: raw.dose_number ?? null,
    status: raw.status,
    actionableToday: raw.actionable_today ?? false,
    earliestDate: date(raw.earliest_date),
    recommendedDate: date(raw.recommended_date),
    overdueAfter: date(raw.overdue_after),
    latestUsefulDate: date(raw.latest_useful_date),
    daysOverdue: raw.days_overdue ?? null,
    givenOn: date(raw.given_on),
    rationale: raw.rationale ?? null,
  };
}

function mapForecast(raw: RawForecast): ImmunisationForecast {
  const date = (v: Nullable<string>) => (v && v !== "null" ? v : null);
  return {
    dateOfBirth: date(raw.date_of_birth),
    asOf: date(raw.as_of),
    ageDays: raw.age_days ?? null,
    doses: (raw.doses ?? []).map(mapForecastDose),
    dueNow: raw.due_now ?? [],
    anyOverdue: raw.any_overdue ?? false,
    provisional: raw.provisional ?? false,
    provisionalReasons: raw.provisional_reasons ?? [],
    scheduleId: raw.schedule_id ?? null,
    scheduleVersion: raw.schedule_version ?? null,
    approvalStatus: raw.approval_status ?? null,
    note: raw.note ?? null,
  };
}

// ---------- growth intelligence ----------

interface RawGrowthSignal {
  code: string;
  name: Nullable<string>;
  severity: Nullable<string>;
  measure: Nullable<string>;
  z_change: Nullable<number>;
  from_age_days: Nullable<number>;
  to_age_days: Nullable<number>;
  action: Nullable<string>;
  referral_required: boolean;
  rationale: Nullable<string>;
}

interface RawGrowthInterpretation {
  assessable: boolean;
  not_assessable_reason: Nullable<string>;
  interpretation_blocked: boolean;
  z_trend_derivable: boolean;
  any_concern: boolean;
  referral_required: boolean;
  contacts_considered: Nullable<number>;
  signals: RawGrowthSignal[];
  content_version: Nullable<string>;
  content_source: Nullable<string>;
  approval_status: Nullable<string>;
  note: Nullable<string>;
}

export interface GrowthSignal {
  code: string;
  name: string | null;
  severity: string | null;
  measure: string | null;
  zChange: number | null;
  fromAgeDays: number | null;
  toAgeDays: number | null;
  action: string | null;
  referralRequired: boolean;
  rationale: string | null;
}

export interface GrowthInterpretation {
  assessable: boolean;
  notAssessableReason: string | null;
  /**
   * A measurement is in question — an implausible jump, or the artefact of a child first measured
   * standing rather than lying down. The engine has already emptied `signals`; the screen must not
   * present a trend or a reassurance in their place.
   */
  interpretationBlocked: boolean;
  /** False = nothing was compared. Distinct from "compared and found nothing". */
  zTrendDerivable: boolean;
  anyConcern: boolean;
  referralRequired: boolean;
  contactsConsidered: number | null;
  signals: GrowthSignal[];
  contentVersion: string | null;
  contentSource: string | null;
  approvalStatus: string | null;
  note: string | null;
}

function mapGrowthInterpretation(raw: RawGrowthInterpretation): GrowthInterpretation {
  return {
    assessable: raw.assessable ?? false,
    notAssessableReason: raw.not_assessable_reason ?? null,
    interpretationBlocked: raw.interpretation_blocked ?? false,
    zTrendDerivable: raw.z_trend_derivable ?? false,
    anyConcern: raw.any_concern ?? false,
    referralRequired: raw.referral_required ?? false,
    contactsConsidered: raw.contacts_considered ?? null,
    signals: (raw.signals ?? []).map((s) => ({
      code: s.code,
      name: s.name ?? null,
      severity: s.severity ?? null,
      measure: s.measure ?? null,
      zChange: s.z_change ?? null,
      fromAgeDays: s.from_age_days ?? null,
      toAgeDays: s.to_age_days ?? null,
      action: s.action ?? null,
      referralRequired: s.referral_required ?? false,
      rationale: s.rationale ?? null,
    })),
    contentVersion: raw.content_version ?? null,
    contentSource: raw.content_source ?? null,
    approvalStatus: raw.approval_status ?? null,
    note: raw.note ?? null,
  };
}

// ---------- danger signs ----------

interface RawDangerAlert {
  rule_id?: Nullable<string>;
  code?: Nullable<string>;
  name?: Nullable<string>;
  severity?: Nullable<string>;
  action?: Nullable<string>;
  required_action?: Nullable<string>;
  referral_required?: boolean;
  overridable?: boolean;
  triggering_findings?: Nullable<string[]>;
  source?: Nullable<string>;
  content_version?: Nullable<string>;
  approval_status?: Nullable<string>;
}

interface RawDangerAssessment {
  incomplete?: boolean;
  unassessed_signs?: Nullable<string[]>;
  unassessed?: Nullable<string[]>;
  alerts?: Nullable<RawDangerAlert[]>;
  referral_required?: boolean;
  content_version?: Nullable<string>;
  approval_status?: Nullable<string>;
  note?: Nullable<string>;
}

export interface DangerAlert {
  id: string;
  name: string | null;
  severity: string | null;
  action: string | null;
  referralRequired: boolean;
  overridable: boolean;
  triggeringFindings: string[];
  source: string | null;
  contentVersion: string | null;
  approvalStatus: string | null;
}

export interface DangerSignAssessment {
  /** True when signs were not assessed. A clean result over this is not an all-clear. */
  incomplete: boolean;
  unassessedSigns: string[];
  alerts: DangerAlert[];
  referralRequired: boolean;
  contentVersion: string | null;
  approvalStatus: string | null;
  note: string | null;
}

function mapDangerSigns(raw: RawDangerAssessment): DangerSignAssessment {
  return {
    incomplete: raw.incomplete ?? false,
    unassessedSigns: raw.unassessed_signs ?? raw.unassessed ?? [],
    alerts: (raw.alerts ?? []).map((a) => ({
      id: a.rule_id ?? a.code ?? "",
      name: a.name ?? null,
      severity: a.severity ?? null,
      action: a.required_action ?? a.action ?? null,
      referralRequired: a.referral_required ?? false,
      overridable: a.overridable ?? true,
      triggeringFindings: a.triggering_findings ?? [],
      source: a.source ?? null,
      contentVersion: a.content_version ?? null,
      approvalStatus: a.approval_status ?? null,
    })),
    referralRequired: raw.referral_required ?? false,
    contentVersion: raw.content_version ?? null,
    approvalStatus: raw.approval_status ?? null,
    note: raw.note ?? null,
  };
}

// ---------- hooks ----------

export const paediatricKeys = {
  imnci: (patientId: string, answersKey: string) => ["paediatric", "imnci", { patientId, answersKey }] as const,
  forecast: (patientId: string, asOf: string | null) =>
    ["paediatric", "immunisation-forecast", { patientId, asOf }] as const,
  growth: (patientId: string, seriesKey: string) => ["paediatric", "growth-interpret", { patientId, seriesKey }] as const,
  dangerSigns: (patientId: string, answersKey: string) =>
    ["paediatric", "danger-signs", { patientId, answersKey }] as const,
};

export interface ImnciRequest {
  patientId: string;
  ageDays: number | null;
  /** The recorded findings, by the fact key the engine names. Omitted means not assessed. */
  findings: Record<string, unknown>;
}

export function useImnciClassification(request: ImnciRequest, enabled = true) {
  const answersKey = JSON.stringify(request.findings);
  return useQuery<ImnciAssessment>({
    queryKey: paediatricKeys.imnci(request.patientId, answersKey),
    queryFn: async () => {
      const response = await apiClient.post<ApiResponse<RawImnci>>(
        "/internal/v1/paediatric/imnci/classify",
        { ageDays: request.ageDays, ...request.findings },
      );
      return mapImnci(response.data);
    },
    // An age is the routing input between the two charts, so without it there is no chart to run.
    enabled: enabled && !!request.patientId && request.ageDays !== null,
    // No retry storm on a 502: the surface should say the engine is unreachable promptly rather
    // than sitting in a spinner that reads as "still checking".
    retry: 1,
  });
}

export interface ForecastRequest {
  patientId: string;
  dateOfBirth: string | null;
  sex?: string | null;
  /** The doses already on the record. PCT owns them; the engine is stateless. */
  doses: Array<{
    vaccineCode: string | null;
    doseNumber: number | null;
    administeredAt: string | null;
    status: string | null;
  }>;
  asOf?: string | null;
}

export function useImmunisationForecast(request: ForecastRequest, enabled = true) {
  return useQuery<ImmunisationForecast>({
    queryKey: paediatricKeys.forecast(request.patientId, request.asOf ?? null),
    queryFn: async () => {
      const response = await apiClient.post<ApiResponse<RawForecast>>(
        "/internal/v1/paediatric/immunisation/forecast",
        {
          date_of_birth: request.dateOfBirth,
          sex: request.sex ?? null,
          as_of: request.asOf ?? null,
          doses: request.doses.map((d) => ({
            vaccine_code: d.vaccineCode,
            dose_number: d.doseNumber,
            occurrence_at: d.administeredAt,
            status: d.status ?? "COMPLETED",
          })),
        },
      );
      return mapForecast(response.data);
    },
    // Every gate in the forecast is a function of age. Without a date of birth the engine refuses
    // with a 400 rather than guessing, so there is no point asking.
    enabled: enabled && !!request.patientId && !!request.dateOfBirth,
    retry: 1,
  });
}

export interface GrowthInterpretRequest {
  patientId: string;
  /** Measurements carrying the z-scores PCT stamped at the time. The engine never recomputes one. */
  measurements: Array<Record<string, unknown>>;
}

export function useGrowthInterpretation(request: GrowthInterpretRequest, enabled = true) {
  const seriesKey = String(request.measurements.length);
  return useQuery<GrowthInterpretation>({
    queryKey: paediatricKeys.growth(request.patientId, seriesKey),
    queryFn: async () => {
      const response = await apiClient.post<ApiResponse<RawGrowthInterpretation>>(
        "/internal/v1/paediatric/growth/interpret",
        { measurements: request.measurements },
      );
      return mapGrowthInterpretation(response.data);
    },
    enabled: enabled && !!request.patientId,
    retry: 1,
  });
}

export interface DangerSignRequest {
  patientId: string;
  ageDays: number | null;
  findings: Record<string, unknown>;
}

export function useDangerSigns(request: DangerSignRequest, enabled = true) {
  const answersKey = JSON.stringify(request.findings);
  return useQuery<DangerSignAssessment>({
    queryKey: paediatricKeys.dangerSigns(request.patientId, answersKey),
    queryFn: async () => {
      const response = await apiClient.post<ApiResponse<RawDangerAssessment>>(
        "/internal/v1/paediatric/danger-signs/evaluate",
        { ageDays: request.ageDays, ...request.findings },
      );
      return mapDangerSigns(response.data);
    },
    enabled: enabled && !!request.patientId && request.ageDays !== null,
    retry: 1,
  });
}
