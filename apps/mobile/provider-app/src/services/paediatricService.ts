/**
 * Paediatric decision support — IMNCI, EPI forecast, growth intelligence and danger signs.
 *
 * Backend: experience-bff `/internal/v1/paediatric/*`, proxying the clinical knowledge platform.
 * The engines are the same ones the web shell calls, reached the same way, and **nothing in this
 * module evaluates anything**. That is the parity requirement: not an identical layout, but the
 * same clinical answer from the same governed content. A phone that classified locally would be a
 * second implementation of the IMNCI tables, and the day it drifted from the national content the
 * two surfaces would disagree about the same child.
 *
 * Five behaviours this module must preserve, each one a distinction the engines went to trouble to
 * make and which a client can destroy in a line:
 *
 *   1. An omitted finding stays omitted. Filling absences with `false` turns every unasked
 *      question into a negative finding, and produces a confident all-clear on a child nobody
 *      examined.
 *   2. `INDETERMINATE` with `missing_inputs` is a successful answer, not an error. It is the engine
 *      saying what it needs; a caller that treats it as a failure loses exactly the actionable part.
 *   3. `actionable_today` is the engine's verdict on whether a dose would immunise the child. It is
 *      never re-derived here from a status name or a date.
 *   4. `interpretation_blocked` means the growth signals were suppressed deliberately. This module
 *      passes the flag through; it never reconstructs a signal list.
 *   5. A 502 (`ApiError.status === 502`) means the engine could not be reached. It is never an
 *      empty result — every empty answer these engines can give is the most reassuring sentence a
 *      clinical surface could show. The BFF returns no `data` key at all on failure precisely so
 *      that this cannot be papered over.
 *
 * Response fields are snake_case because that is exactly what arrives over the wire from the
 * knowledge platform through the BFF, not what would be conventional in TypeScript.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/paediatric";

// --- IMNCI ---------------------------------------------------------------------------------

export type ImnciOutcomeStatus = "OK" | "INDETERMINATE" | "NOT_APPLICABLE" | string;

export interface ImnciOutcome {
  table_id: string;
  axis: string | null;
  status: ImnciOutcomeStatus;
  classification: string | null;
  classification_name: string | null;
  /** PINK | YELLOW | GREEN, or null when the row did not resolve. Never defaulted. */
  colour: string | null;
  /** A severer row on the same chart could not be excluded. */
  provisional: boolean;
  actionable: boolean;
  unresolved_tiers: string[] | null;
  /** The observations that would resolve an INDETERMINATE row. */
  missing_inputs: string[] | null;
  waived_criteria: string[] | null;
  action: string | null;
  treatments: string[] | null;
  referral_required: boolean;
  urgent_referral: boolean;
  rationale: string | null;
}

export interface ImnciAssessment {
  age_days: number | null;
  /** CHILD | YOUNG_INFANT — which chart ran. */
  pathway: string | null;
  applicable: boolean;
  classifications: ImnciOutcome[];
  urgent_referral_required: boolean;
  referral_required: boolean;
  /** Something was not assessed. A complete-looking result must never be shown over this. */
  incomplete: boolean;
  outstanding_assessments: string[] | null;
  unavailable_criteria: string[] | null;
  treatment_plan: string[] | null;
  disposition: string | null;
  content_version: string | null;
  content_source: string | null;
  approval_status: string | null;
  note: string | null;
}

/**
 * @param findings the recorded findings keyed as the engine names them. A key that is absent must
 *                 stay absent — see behaviour 1 above.
 */
export async function classifyImnci(
  ageDays: number,
  findings: Record<string, unknown>,
): Promise<ImnciAssessment> {
  const response = await apiClient.post<{ data: ImnciAssessment }>(`${BASE}/imnci/classify`, {
    ageDays,
    ...findings,
  });
  return response.data.data;
}

// --- EPI immunisation forecast -------------------------------------------------------------

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

export interface ForecastDose {
  series: string;
  antigen: string | null;
  dose_number: number | null;
  status: DoseStatus;
  /** The engine's verdict on whether giving this today would immunise the child. */
  actionable_today: boolean;
  /** For BLOCKED_BY_INTERVAL, the date the gate opens. */
  earliest_date: string | null;
  recommended_date: string | null;
  overdue_after: string | null;
  /** For AGED_OUT, the date the window closed. */
  latest_useful_date: string | null;
  days_overdue: number | null;
  given_on: string | null;
  /** For AGED_OUT, the safety reason. */
  rationale: string | null;
}

export interface ImmunisationForecast {
  date_of_birth: string | null;
  as_of: string | null;
  age_days: number | null;
  doses: ForecastDose[];
  due_now: string[] | null;
  any_overdue: boolean;
  provisional: boolean;
  provisional_reasons: string[] | null;
  schedule_id: string | null;
  schedule_version: string | null;
  approval_status: string | null;
  note: string | null;
}

export interface RecordedDose {
  vaccine_code: string | null;
  dose_number: number | null;
  occurrence_at: string | null;
  status: string | null;
}

export async function forecastImmunisation(params: {
  dateOfBirth: string;
  sex?: string | null;
  doses: RecordedDose[];
  asOf?: string | null;
}): Promise<ImmunisationForecast> {
  const response = await apiClient.post<{ data: ImmunisationForecast }>(
    `${BASE}/immunisation/forecast`,
    {
      date_of_birth: params.dateOfBirth,
      sex: params.sex ?? null,
      as_of: params.asOf ?? null,
      doses: params.doses,
    },
  );
  return response.data.data;
}

/**
 * The doses to offer at this contact.
 *
 * Reads `actionable_today` and nothing else. A local re-derivation would be a second copy of the
 * minimum-interval and age-ceiling rules on a phone, and a dose given because this list said so
 * but before its interval elapsed does not immunise the child — it is discarded and they must be
 * re-vaccinated.
 */
export function giveableToday(forecast: ImmunisationForecast): ForecastDose[] {
  return forecast.doses.filter((d) => d.actionable_today);
}

/** Doses withheld for a stated reason. Shown, because "not due" without a reason sends a child back tomorrow. */
export function withheldToday(forecast: ImmunisationForecast): ForecastDose[] {
  return forecast.doses.filter(
    (d) =>
      !d.actionable_today &&
      (d.status === "BLOCKED_BY_INTERVAL" ||
        d.status === "AGED_OUT" ||
        d.status === "AWAITING_PREVIOUS_DOSE"),
  );
}

// --- Growth intelligence -------------------------------------------------------------------

export interface GrowthSignal {
  code: string;
  name: string | null;
  severity: string | null;
  measure: string | null;
  z_change: number | null;
  from_age_days: number | null;
  to_age_days: number | null;
  action: string | null;
  referral_required: boolean;
  rationale: string | null;
}

export interface GrowthInterpretation {
  assessable: boolean;
  not_assessable_reason: string | null;
  /** The engine suppressed the signals because a measurement is in question. */
  interpretation_blocked: boolean;
  /** False = nothing was compared. Distinct from "compared and found nothing". */
  z_trend_derivable: boolean;
  any_concern: boolean;
  referral_required: boolean;
  contacts_considered: number | null;
  signals: GrowthSignal[];
  content_version: string | null;
  content_source: string | null;
  approval_status: string | null;
  note: string | null;
}

/** @param measurements the stamped z-scores from PCT. The engine never recomputes one. */
export async function interpretGrowth(
  measurements: Array<Record<string, unknown>>,
): Promise<GrowthInterpretation> {
  const response = await apiClient.post<{ data: GrowthInterpretation }>(`${BASE}/growth/interpret`, {
    measurements,
  });
  return response.data.data;
}

// --- Danger signs --------------------------------------------------------------------------

export interface DangerAlert {
  rule_id?: string | null;
  code?: string | null;
  name?: string | null;
  severity?: string | null;
  required_action?: string | null;
  action?: string | null;
  referral_required?: boolean;
  overridable?: boolean;
  triggering_findings?: string[] | null;
  source?: string | null;
  content_version?: string | null;
  approval_status?: string | null;
}

export interface DangerSignAssessment {
  /** Signs were not assessed. A clean result over this is not an all-clear. */
  incomplete: boolean;
  unassessed_signs?: string[] | null;
  unassessed?: string[] | null;
  alerts?: DangerAlert[] | null;
  referral_required?: boolean;
  content_version?: string | null;
  approval_status?: string | null;
  note?: string | null;
}

export async function evaluateDangerSigns(
  ageDays: number,
  findings: Record<string, unknown>,
): Promise<DangerSignAssessment> {
  const response = await apiClient.post<{ data: DangerSignAssessment }>(
    `${BASE}/danger-signs/evaluate`,
    { ageDays, ...findings },
  );
  return response.data.data;
}

export function unassessedSigns(assessment: DangerSignAssessment): string[] {
  return assessment.unassessed_signs ?? assessment.unassessed ?? [];
}

// --- shared -------------------------------------------------------------------------------

/**
 * Whether a caught error is the engine being unreachable.
 *
 * Callers use this to choose between "could not ask" and any clinical statement. There is no third
 * option: on a paediatric surface there is no safe way to render an unreachable engine as data.
 */
export function isEngineUnavailable(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    (error as { status: number }).status >= 502
  );
}

/** The sick young infant chart covers birth to two months; the child chart two months to five years. */
export const YOUNG_INFANT_MAX_AGE_DAYS = 59;
export const UNDER_FIVE_MAX_AGE_DAYS = 1824;

export function ageDaysFromDateOfBirth(dateOfBirth: string | null | undefined, now = new Date()): number | null {
  if (!dateOfBirth) return null;
  const dob = new Date(`${dateOfBirth.slice(0, 10)}T00:00:00Z`);
  if (Number.isNaN(dob.getTime())) return null;
  const days = Math.floor((now.getTime() - dob.getTime()) / 86_400_000);
  return days < 0 ? null : days;
}
