/**
 * Maternity — Partograph and CTG service client.
 *
 * Backend: experience-bff `/internal/v1/maternity/partograph/*` and `/internal/v1/maternity/ctg/*`,
 * proxying canonical PCT (`MaternityController` / `MaternityService`, migration V056). PCT owns the
 * record and the progress assessment; this module composes only.
 *
 * Contract: docs/clinical/rmnp/partograph-ctg-mobile-contract.md — in particular §4's six behaviours:
 *
 *   1. "No session is open" is a 200 carrying `{..._active: false}` — a real answer, not an error.
 *   2. A 502 (`ApiError.code === "PCT_UNAVAILABLE"`) means the record could not be read — never that
 *      it is empty. Callers must render "unavailable" in that case, never a blank chart.
 *   3. `addPartographPoint` returns the progress assessment alongside the saved point, so a reading
 *      that crosses the action line reaches the caller immediately, not after a refetch.
 *   4. `INSUFFICIENT_DATA` is the most alarming status, not a calm default — rendering is the
 *      caller's job, but this module must never coerce it into something softer.
 *   5. Nothing here pre-fills a previous value into a new observation; every point is exactly what
 *      the caller passes.
 *   6. CTG chunks carry `missing_sample_count`; this module surfaces it rather than hiding it.
 *
 * Request bodies use camelCase (the web shell's convention; PCT's `MaternityService` accepts both
 * spellings for every multi-word key — verified by reading every accessor). Every response field
 * PCT emits is snake_case (`MaternityService.toMap`), so the response types below are snake_case
 * to match exactly what arrives over the wire, not what would be conventional in TypeScript.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/maternity";

// --- Partograph ---------------------------------------------------------------------------

export type PartographProgressStatus =
  | "LEFT_OF_ALERT"
  | "BETWEEN_ALERT_AND_ACTION"
  | "AT_OR_RIGHT_OF_ACTION"
  | "SECOND_STAGE"
  | "INSUFFICIENT_DATA";
// PartographProgressEngine.ProgressStatus has exactly these five values. The mobile contract
// doc also names "LATENT_PHASE_NOT_ASSESSED" as a status, but the engine returns
// INSUFFICIENT_DATA for that case too (distinguished only by `recommended_action`/`observations`
// text) — verified against PartographProgressEngine.java directly rather than the doc prose.

export interface PartographProgress {
  status: PartographProgressStatus;
  latest_dilation_cm: number | null;
  latest_dilation_at: string | null;
  expected_dilation_cm: number | null;
  hours_behind_alert_line: number | null;
  alert_reference_at: string | null;
  alert_reference_dilation_cm: number | null;
  /** Observations whose WHO interval has lapsed — never short-circuited to empty. */
  outstanding_observations: string[];
  /** Human-readable notes explaining the assessment. */
  observations: string[];
  recommended_action: string | null;
  content_version: string;
  content_source: string;
}

export interface PartographSession {
  session_id: string;
  patient_id: string;
  journey_id: string | null;
  encounter_id: string | null;
  status: "ACTIVE" | "CLOSED";
  labour_phase: string;
  started_at: string;
  started_by: string | null;
  closed_at: string | null;
  closed_by: string | null;
  outcome: string | null;
  summary_notes: string | null;
  alert_reference_at: string | null;
  alert_reference_dilation_cm: number | null;
  content_version: string;
}

export interface PartographSessionWithProgress extends PartographSession {
  progress: PartographProgress;
}

export interface LabourObservation {
  observation_id: string;
  patient_id: string;
  journey_id: string | null;
  encounter_id: string | null;
  partograph_session_id: string | null;
  observed_at: string;
  recorded_by: string | null;
  phase: string;
  fetal_heart_rate_bpm: number | null;
  liquor: string | null;
  moulding: string | null;
  caput: string | null;
  cervical_dilation_cm: number | null;
  fetal_descent_fifths: number | null;
  contraction_frequency_10min: number | null;
  contraction_duration_sec: number | null;
  maternal_pulse_bpm: number | null;
  systolic_bp: number | null;
  diastolic_bp: number | null;
  temperature_c: number | null;
  urine_volume_ml: number | null;
  urine_protein: string | null;
  urine_acetone: string | null;
  maternal_condition: string | null;
  oxytocin_rate_miu_min: number | null;
  notes: string | null;
}

export interface PartographSessionDetail extends PartographSessionWithProgress {
  points: LabourObservation[];
}

export type PartographActiveResult =
  | { partographActive: true; session: PartographSessionWithProgress }
  | { partographActive: false; patientId: string };

/** Open a partograph session. PCT enforces one active session per patient (reopens if already open). */
export async function openPartograph(params: {
  patientId: string;
  journeyId?: string;
  encounterId?: string;
  labourPhase?: string;
}): Promise<PartographSession> {
  const response = await apiClient.post<{ data: PartographSession }>(`${BASE}/partograph/sessions`, {
    patientId: params.patientId,
    journeyId: params.journeyId,
    encounterId: params.encounterId,
    labourPhase: params.labourPhase,
  });
  return response.data.data;
}

/**
 * The open partograph for a patient, if there is one. "None open" is PCT's 200 answer and comes
 * back here as `{ partographActive: false }` — callers must not treat that as a failure, and must
 * not treat a thrown `ApiError` (the 502 case) as this either. See behaviours 1 and 2 above.
 */
export async function getActivePartograph(
  patientId: string,
  encounterId?: string,
): Promise<PartographActiveResult> {
  const qs = new URLSearchParams({ patientId, ...(encounterId ? { encounterId } : {}) });
  const response = await apiClient.get<{ data: Record<string, unknown> }>(
    `${BASE}/partograph/sessions/active?${qs.toString()}`,
  );
  const data = response.data.data;
  if (data.partograph_active === false) {
    return { partographActive: false, patientId: String(data.patient_id ?? patientId) };
  }
  return { partographActive: true, session: data as unknown as PartographSessionWithProgress };
}

/** A session with its plotted points and current assessment. */
export async function getPartograph(sessionId: string): Promise<PartographSessionDetail> {
  const response = await apiClient.get<{ data: PartographSessionDetail }>(
    `${BASE}/partograph/sessions/${encodeURIComponent(sessionId)}`,
  );
  return response.data.data;
}

export interface AddPartographPointResult {
  point: LabourObservation;
  progress: PartographProgress;
}

/**
 * Records one observation. `answers` should be exactly what the clinician entered on the governed
 * `impilo.labour.partograph.observation.v1` form this call — nothing here fills in a value the
 * caller did not supply, and callers must not seed `answers` from a previous point either.
 */
export async function addPartographPoint(
  sessionId: string,
  answers: Record<string, unknown>,
): Promise<AddPartographPointResult> {
  const response = await apiClient.post<{ data: AddPartographPointResult }>(
    `${BASE}/partograph/sessions/${encodeURIComponent(sessionId)}/points`,
    answers,
  );
  return response.data.data;
}

export async function closePartograph(
  sessionId: string,
  params?: { outcome?: string; summaryNotes?: string },
): Promise<PartographSession> {
  const response = await apiClient.patch<{ data: PartographSession }>(
    `${BASE}/partograph/sessions/${encodeURIComponent(sessionId)}/close`,
    params ?? {},
  );
  return response.data.data;
}

// --- CTG -----------------------------------------------------------------------------------

export interface CtgSession {
  session_id: string;
  patient_id: string;
  journey_id: string | null;
  encounter_id: string | null;
  status: "ACTIVE" | "CLOSED";
  monitoring_mode: string;
  device_id: string | null;
  started_at: string;
  started_by: string | null;
  closed_at: string | null;
  baseline_fhr_bpm: number | null;
  baseline_maternal_hr_bpm: number | null;
  summary_notes: string | null;
}

export interface CtgAnnotation {
  annotation_id: string;
  session_id: string;
  recorded_at: string;
  category: string;
  channel: string | null;
  sample_offset_sec: number | null;
  value: string | null;
  severity: string | null;
  notes: string | null;
  recorded_by: string | null;
}

export interface CtgSessionDetail extends CtgSession {
  annotations: CtgAnnotation[];
}

/**
 * A device sample chunk. `missing_sample_count` is the shortfall between the declared and actual
 * sample count — a real gap where the transducer lost contact. Never interpolated by PCT, and
 * must never be interpolated on render either: see behaviour 6.
 */
export interface CtgChunk {
  chunk_id: string;
  session_id: string;
  channel: string;
  started_at: string;
  ended_at: string | null;
  sample_rate_hz: number;
  sample_count: number;
  missing_sample_count: number;
  duration_seconds: number;
  unit: string;
  captured_by: string | null;
  device_id: string | null;
  notes: string | null;
  samples: number[] | null;
  samples_error?: string;
}

export type CtgActiveResult =
  | { ctgActive: true; session: CtgSession }
  | { ctgActive: false; patientId: string };

export async function openCtgSession(params: {
  patientId: string;
  journeyId?: string;
  encounterId?: string;
  monitoringMode?: string;
  deviceId?: string;
}): Promise<CtgSession> {
  const response = await apiClient.post<{ data: CtgSession }>(`${BASE}/ctg/sessions`, {
    patientId: params.patientId,
    journeyId: params.journeyId,
    encounterId: params.encounterId,
    monitoringMode: params.monitoringMode,
    deviceId: params.deviceId,
  });
  return response.data.data;
}

/** "Nobody is being monitored right now" is a real 200 answer — same rule as partograph. */
export async function getActiveCtgSession(patientId: string, encounterId?: string): Promise<CtgActiveResult> {
  const qs = new URLSearchParams({ patientId, ...(encounterId ? { encounterId } : {}) });
  const response = await apiClient.get<{ data: Record<string, unknown> }>(
    `${BASE}/ctg/sessions/active?${qs.toString()}`,
  );
  const data = response.data.data;
  if (data.ctg_active === false) {
    return { ctgActive: false, patientId: String(data.patient_id ?? patientId) };
  }
  return { ctgActive: true, session: data as unknown as CtgSession };
}

export async function getCtgSession(sessionId: string): Promise<CtgSessionDetail> {
  const response = await apiClient.get<{ data: CtgSessionDetail }>(
    `${BASE}/ctg/sessions/${encodeURIComponent(sessionId)}`,
  );
  return response.data.data;
}

/** Device trace chunks. This module never adds a chunk — samples arrive from the monitor, not the app. */
export async function getCtgChunks(
  sessionId: string,
  params?: { channel?: string; from?: string; to?: string },
): Promise<CtgChunk[]> {
  const qs = new URLSearchParams();
  if (params?.channel) qs.set("channel", params.channel);
  if (params?.from) qs.set("from", params.from);
  if (params?.to) qs.set("to", params.to);
  const suffix = qs.toString() ? `?${qs.toString()}` : "";
  const response = await apiClient.get<{ data: CtgChunk[] }>(
    `${BASE}/ctg/sessions/${encodeURIComponent(sessionId)}/chunks${suffix}`,
  );
  return response.data.data;
}

/**
 * Records a clinician's reading of the trace via the governed `impilo.labour.ctg.annotation.v1`
 * form. `severity` is the clinician's judgement alone — nothing here or upstream derives it from
 * the trace.
 */
export async function addCtgAnnotation(
  sessionId: string,
  answers: Record<string, unknown>,
): Promise<CtgAnnotation> {
  const response = await apiClient.post<{ data: CtgAnnotation }>(
    `${BASE}/ctg/sessions/${encodeURIComponent(sessionId)}/annotations`,
    answers,
  );
  return response.data.data;
}

// --- Maternal near-miss ----------------------------------------------------------------------
//
// Backend: experience-bff `/internal/v1/clinical/maternal/near-miss/classify-form`, NOT under
// `${BASE}` — near-miss identification is ordinary clinical classification, deliberately not on
// the confidential lane the rest of this module's postnatal-adjacent siblings might be, and it
// composes CKP rather than pct-service. See `MaternalNearMissController.java`.
//
// The contract this function must not break, straight from that controller's doc comment:
// blank ≠ ABSENT ≠ unrecognised. `answers` should be exactly the linkId → value map the governed
// `impilo.maternal.nearmiss.assessment.v1` form (form 21) collected — a field the clinician left
// blank must be OMITTED from the map, never sent as "ABSENT", or a near-miss becomes a normal
// birth in the register. An answer the BFF cannot read comes back as a 422
// (`ApiError.code === "unrecognised_form_answer"`) naming which one; that must reach the
// clinician, not be swallowed.

export interface NearMissClassification {
  classification_code: string | null;
  classification_name: string | null;
  /** One of the classification engine's four states: CLASSIFIED, NOT_APPLICABLE, NOT_ASSESSED, INDETERMINATE. */
  status: string;
  is_near_miss: boolean;
  /** True when a more severe row above this one could not be excluded — a floor, not a conclusion. */
  provisional: boolean;
  unresolved_criteria: string[];
  missing_inputs: string[];
  rationale: string | null;
  review_required: boolean;
  review_note: string | null;
}

export interface NearMissClassifyMeta {
  request_id?: string;
  correlation_id?: string;
  form_key?: string;
  criteria_recorded?: number;
  /** Bare field names (no `nearMiss.` prefix) left blank — named, not just counted. */
  criteria_left_blank?: string[];
}

export interface NearMissClassifyResult {
  data: NearMissClassification;
  meta: NearMissClassifyMeta;
}

const NEAR_MISS_FORM_KEY = "impilo.maternal.nearmiss.assessment.v1";

/**
 * Classifies a woman from form-21 answers. Never called with a `formKey` other than form 21's own
 * unless a caller has a specific reason to override it — the BFF defaults to it anyway, but this
 * makes the request self-describing on the wire.
 */
export async function classifyNearMissForm(
  answers: Record<string, unknown>,
  formKey: string = NEAR_MISS_FORM_KEY,
): Promise<NearMissClassifyResult> {
  const response = await apiClient.post<NearMissClassifyResult>(
    "/internal/v1/clinical/maternal/near-miss/classify-form",
    { formKey, answers },
  );
  return response.data;
}

// --- Severe maternal outcome indicators ------------------------------------------------------
//
// Backend: `POST /internal/v1/clinical/maternal/near-miss/indicators` — the near-miss-to-death
// ratio and mortality index for a reporting period's cases. A ratio computed over zero deaths (or
// an index over zero severe outcomes) is `null` — mathematically undefined, not zero — and must
// never be rendered as 0 or dropped from what is shown. An unrecognised outcome value is CKP's
// 422 (`ApiError.code === "unclassifiable_outcome"` when the BFF surfaces its own refusal shape;
// this proxy forwards CKP's whole response, so callers reading `error.details` may instead need
// `error.message`/`error.status` — see `MaternalNearMissController.java`'s `indicators` doc
// comment) and must not be silently bucketed into NOT_SEVERE or NEAR_MISS_INDETERMINATE.

export type NearMissIndicatorOutcome =
  | "NEAR_MISS"
  | "MATERNAL_DEATH"
  | "NEAR_MISS_INDETERMINATE"
  | "NOT_SEVERE";

export interface NearMissIndicatorsResult {
  indicator_period: string | null;
  near_miss_count: number;
  maternal_death_count: number;
  indeterminate_count: number;
  severe_maternal_outcome_count: number;
  near_miss_to_death_ratio: number | null;
  near_miss_to_death_ratio_upper_bound: number | null;
  mortality_index: number | null;
  mortality_index_lower_bound: number | null;
  mortality_index_direction: string;
  note: string | null;
}

/**
 * Computes the ratio/index for a period. `cases` is forwarded exactly as given — this function
 * invents no outcome for a case the caller did not label, because the two "reasonable" defaults
 * (NOT_SEVERE or NEAR_MISS_INDETERMINATE) are precisely the failure modes CKP's 422 exists to
 * refuse rather than let a caller guess.
 */
export async function computeNearMissIndicators(
  indicatorPeriod: string,
  cases: { outcome: NearMissIndicatorOutcome }[],
): Promise<NearMissIndicatorsResult> {
  const response = await apiClient.post<{ data: NearMissIndicatorsResult }>(
    "/internal/v1/clinical/maternal/near-miss/indicators",
    { indicatorPeriod, cases },
  );
  return response.data.data;
}

// --- Bishop score (cervical favourability) ---------------------------------------------------
//
// Backend: experience-bff `/internal/v1/clinical/maternal/bishop/classify-form`. Assessment only —
// nothing persisted. Blank components stay omitted so CKP returns INCOMPLETE rather than inventing zero.

export type BishopInterpretation = "UNFAVOURABLE" | "INTERMEDIATE" | "FAVOURABLE" | "INCOMPLETE";

export interface BishopScoreAssessment {
  score: number | null;
  interpretation: BishopInterpretation;
  components: Record<string, number>;
  missing: string[];
  content_version: string;
}

export const BISHOP_FORM_KEY = "impilo.maternal.bishop.v1";

export async function assessBishopScoreForm(
  answers: Record<string, unknown>,
  formKey: string = BISHOP_FORM_KEY,
): Promise<BishopScoreAssessment> {
  const response = await apiClient.post<{ data: BishopScoreAssessment }>(
    "/internal/v1/clinical/maternal/bishop/classify-form",
    { formKey, answers },
  );
  return response.data.data;
}

// --- Emergency bundles (PPH, eclampsia) ------------------------------------------------------
//
// Backend: experience-bff `/internal/v1/clinical/maternal/emergency-bundles/{pph|eclampsia}/assess`.
// Stateless checklist verdict from CKP's EmergencyBundleEngine — the episode and its recorded steps
// are persisted by the caller's encounter record, not here. A null `controlConfirmed` must stay
// omitted (never sent as false): unknown control is not "not controlled".

export type EmergencyBundleStepStatus = "DONE" | "DUE" | "OVERDUE" | "NOT_YET_DUE";
export type EmergencyBundleStatus = "ACTIVE" | "CLOSABLE" | "LAPSED_UNRESOLVED";

export interface EmergencyBundleStep {
  code: string;
  name: string;
  status: EmergencyBundleStepStatus;
  mandatory: boolean;
  action: string | null;
}

export interface EmergencyBundleAssessment {
  status: EmergencyBundleStatus;
  steps: EmergencyBundleStep[];
  outstanding_mandatory: string[];
  may_close: boolean;
  note: string;
}

export interface EmergencyBundleAssessInput {
  completedSteps: string[];
  minutesSinceTrigger: number;
  minutesSinceLastObservation: number;
  controlConfirmed?: boolean | null;
  clinicianConfirmedClose?: boolean;
}

export type EmergencyBundleKind = "pph" | "eclampsia";

export async function assessEmergencyBundle(
  kind: EmergencyBundleKind,
  input: EmergencyBundleAssessInput,
): Promise<EmergencyBundleAssessment> {
  const body: Record<string, unknown> = {
    completedSteps: input.completedSteps,
    minutesSinceTrigger: input.minutesSinceTrigger,
    minutesSinceLastObservation: input.minutesSinceLastObservation,
  };
  if (input.controlConfirmed != null) {
    body.controlConfirmed = input.controlConfirmed;
  }
  if (input.clinicianConfirmedClose != null) {
    body.clinicianConfirmedClose = input.clinicianConfirmedClose;
  }
  const path =
    kind === "pph"
      ? "/internal/v1/clinical/maternal/emergency-bundles/pph/assess"
      : "/internal/v1/clinical/maternal/emergency-bundles/eclampsia/assess";
  const response = await apiClient.post<{ data: EmergencyBundleAssessment }>(path, body);
  return response.data.data;
}

// --- Birth destination -------------------------------------------------------------------------
//
// Backend: `GET /internal/v1/maternity/birth-destination`, NOT under `${BASE}` — it composes
// tuso's facility status-summary and EmONC readiness rather than pct-service. See
// `BirthDestinationService.java`'s three honesty rules, which this function must not launder:
//
//   1. `NOT_OPERATIONAL` covers both "assessed closed" and "operational status unknown" — an
//      unconfirmed facility is never a safe destination by default.
//   2. `CAPABILITY_UNKNOWN` ("nobody has assessed this") must render distinctly from
//      `BELOW_REQUIRED_LEVEL` ("assessed, and it cannot") — collapsing the two turns the common
//      case (unassessed) into a false refusal.
//   3. A 502 (`status: "UNAVAILABLE"`) means tuso could not be reached. This function does not
//      catch that failure — it propagates as a thrown `ApiError`, the same discipline as
//      `getActivePartograph`'s PCT_UNAVAILABLE case, because a birth destination is not a place to
//      render a network error as a clinical verdict in either direction.

export type BirthDestinationRequiredLevel = "CEMONC" | "BEMONC" | "UNKNOWN";

export type BirthDestinationStatus =
  | "MEETS_REQUIRED_LEVEL"
  | "BELOW_REQUIRED_LEVEL"
  | "CAPABILITY_UNKNOWN"
  | "NOT_OPERATIONAL"
  | "REQUIRED_LEVEL_UNKNOWN"
  | "UNAVAILABLE";

/** Exactly `BirthDestinationController#toMap`'s field set. */
export interface BirthDestinationVerdict {
  facility_id: number;
  required_level: BirthDestinationRequiredLevel;
  status: BirthDestinationStatus;
  operational_gate_passed: boolean;
  emonc_verdict: string | null;
  call_ahead: boolean;
  message: string;
  guidance: string;
}

export async function fetchBirthDestination(
  facilityId: number,
  requiredLevel: BirthDestinationRequiredLevel = "UNKNOWN",
): Promise<BirthDestinationVerdict> {
  const qs = new URLSearchParams({ facilityId: String(facilityId), requiredLevel });
  const response = await apiClient.get<{ data: BirthDestinationVerdict }>(
    `/internal/v1/maternity/birth-destination?${qs.toString()}`,
  );
  return response.data.data;
}

// --- Maternity summary ---------------------------------------------------------------------------
//
// Backend: `GET ${BASE}/summary`, a pure proxy to PCT's `MaternityController#summary` — see
// `MaternityService.summary`. A 502 (`ApiError.code === "maternity_summary_unavailable"`) is an
// outage, not "no maternity record for this woman" — this function propagates it exactly like
// `getActivePartograph` propagates `PCT_UNAVAILABLE`, so a caller cannot render it as an empty
// dashboard.

export interface MaternitySummaryProgress {
  status: string;
  latest_dilation_cm: number | null;
  expected_dilation_cm: number | null;
  hours_behind_alert_line: number | null;
  outstanding_observations: string[];
  observations: string[];
  recommended_action: string | null;
  content_version: string;
  content_source: string;
}

/** Exactly `MaternityService#summary`'s field set. */
export interface MaternitySummary {
  patient_id: string;
  partograph_active: boolean;
  partograph_session_id?: string;
  progress?: MaternitySummaryProgress;
  ctg_active: boolean;
  ctg_session_id?: string;
  observation_count: number;
  last_observed_at: string | null;
}

export async function getMaternitySummary(patientId: string, encounterId?: string): Promise<MaternitySummary> {
  const qs = new URLSearchParams({ patientId, ...(encounterId ? { encounterId } : {}) });
  const response = await apiClient.get<{ data: MaternitySummary }>(`${BASE}/summary?${qs.toString()}`);
  return response.data.data;
}

// --- Confidential reproductive reads (W13-B) ---------------------------------------------------
//
// Backend: experience-bff `/internal/v1/confidential/reproductive/**` — see
// `ConfidentialReproductiveReadController.java`. Empty list = withhold (indistinguishable from
// absence). A 502 (`ApiError.code === "PCT_UNAVAILABLE"`) propagates as a thrown `ApiError` —
// never render that as "no records".

const REPRODUCTIVE_BASE = "/internal/v1/confidential/reproductive";
const MATERNITY_CONFIDENTIAL_BASE = "/internal/v1/confidential/maternity";

export interface ContraceptionCoverage {
  contraceptive_episode_id: string;
  subject_cpid: string;
  method_code: string | null;
  method_class: string | null;
  status: string | null;
  coverage_status: string | null;
  days_remaining: number | null;
  next_due_on: string | null;
  coverage_unknown_why: string | null;
}

export interface ContraceptiveEpisode {
  contraceptive_episode_id: string;
  subject_cpid: string;
  method_code: string | null;
  method_class: string | null;
  status: string | null;
  started_on: string | null;
  ended_on: string | null;
}

export interface PregnancyLossRecord {
  loss_record_id: string;
  mother_cpid: string;
  loss_type: string | null;
  occurred_on: string | null;
}

export interface TopAuthorisation {
  authorisation_id: string;
  subject_cpid: string;
  status: string | null;
  legal_ground: string | null;
  gestation_weeks_at_request: number | null;
  consent_given: boolean | null;
}

export interface TerminationProcedure {
  procedure_id: string;
  subject_cpid: string;
  method: string | null;
  performed_on: string | null;
}

export interface ConfidentialPregnancyEpisode {
  pregnancy_episode_id: string;
  subject_cpid: string;
  status: string;
  estimated_delivery_date: string | null;
  dating_method: string | null;
  risk_status: string | null;
  ended_on: string | null;
}

export async function fetchContraceptionCoverage(
  subjectCpid: string,
  asOf?: string,
): Promise<ContraceptionCoverage[]> {
  const suffix = asOf ? `?asOf=${encodeURIComponent(asOf)}` : "";
  const response = await apiClient.get<{ data: ContraceptionCoverage[] | null }>(
    `${REPRODUCTIVE_BASE}/contraception/${encodeURIComponent(subjectCpid)}${suffix}`,
  );
  return response.data.data ?? [];
}

export async function fetchContraceptionHistory(subjectCpid: string): Promise<ContraceptiveEpisode[]> {
  const response = await apiClient.get<{ data: ContraceptiveEpisode[] | null }>(
    `${REPRODUCTIVE_BASE}/contraception/${encodeURIComponent(subjectCpid)}/history`,
  );
  return response.data.data ?? [];
}

export async function fetchPregnancyLosses(motherCpid: string): Promise<PregnancyLossRecord[]> {
  const response = await apiClient.get<{ data: PregnancyLossRecord[] | null }>(
    `${REPRODUCTIVE_BASE}/losses/${encodeURIComponent(motherCpid)}`,
  );
  return response.data.data ?? [];
}

export async function fetchTopAuthorisations(subjectCpid: string): Promise<TopAuthorisation[]> {
  const response = await apiClient.get<{ data: TopAuthorisation[] | null }>(
    `${REPRODUCTIVE_BASE}/top-authorisations/${encodeURIComponent(subjectCpid)}`,
  );
  return response.data.data ?? [];
}

export async function fetchTerminations(subjectCpid: string): Promise<TerminationProcedure[]> {
  const response = await apiClient.get<{ data: TerminationProcedure[] | null }>(
    `${REPRODUCTIVE_BASE}/terminations/${encodeURIComponent(subjectCpid)}`,
  );
  return response.data.data ?? [];
}

/** Obstetric history for a patient CPID on the confidential maternity lane. */
export async function fetchPatientPregnancyEpisodes(
  patientCpid: string,
): Promise<ConfidentialPregnancyEpisode[]> {
  const response = await apiClient.get<{ data: ConfidentialPregnancyEpisode[] | null }>(
    `${MATERNITY_CONFIDENTIAL_BASE}/pregnancy-episodes/${encodeURIComponent(patientCpid)}`,
  );
  return response.data.data ?? [];
}

export async function fetchPatientCurrentPregnancy(
  patientCpid: string,
): Promise<ConfidentialPregnancyEpisode | null> {
  const response = await apiClient.get<{ data: ConfidentialPregnancyEpisode | null }>(
    `${MATERNITY_CONFIDENTIAL_BASE}/pregnancy-episodes/${encodeURIComponent(patientCpid)}/current`,
  );
  return response.data.data ?? null;
}

// --- W14-B: intention, preconception, fertility, delivery -----------------------------

export interface ReproductiveIntention {
  intention_id: string;
  subject_cpid: string;
  intention: string | null;
  timeframe_months: number | null;
  status: string | null;
  recorded_at: string | null;
}

export interface PreconceptionPlan {
  preconception_plan_id: string;
  subject_cpid: string;
  status: string | null;
  opened_on: string | null;
  folic_acid_started_on: string | null;
}

export interface FertilityEpisode {
  fertility_episode_id: string;
  subject_cpid: string;
  status: string | null;
  opened_on: string | null;
  months_trying: number | null;
}

export interface DeliveryRecord {
  delivery_record_id: string;
  mother_cpid: string;
  pregnancy_episode_id: string | null;
  delivered_at: string | null;
  delivery_mode: string | null;
  babies_delivered: number | null;
}

export async function fetchCurrentReproductiveIntention(
  subjectCpid: string,
): Promise<ReproductiveIntention | null> {
  const response = await apiClient.get<{ data: ReproductiveIntention | null }>(
    `${REPRODUCTIVE_BASE}/reproductive-intentions/${encodeURIComponent(subjectCpid)}/current`,
  );
  return response.data.data ?? null;
}

export async function fetchActivePreconceptionPlan(
  subjectCpid: string,
): Promise<PreconceptionPlan | null> {
  const response = await apiClient.get<{ data: PreconceptionPlan | null }>(
    `${REPRODUCTIVE_BASE}/preconception-plans/${encodeURIComponent(subjectCpid)}/active`,
  );
  return response.data.data ?? null;
}

export async function fetchCurrentFertilityEpisode(
  subjectCpid: string,
): Promise<FertilityEpisode | null> {
  const response = await apiClient.get<{ data: FertilityEpisode | null }>(
    `${REPRODUCTIVE_BASE}/fertility-episodes/${encodeURIComponent(subjectCpid)}/current`,
  );
  return response.data.data ?? null;
}

export async function fetchDeliveryRecordsForMother(
  motherCpid: string,
): Promise<DeliveryRecord[]> {
  const response = await apiClient.get<{ data: DeliveryRecord[] | null }>(
    `${REPRODUCTIVE_BASE}/delivery-records/mother/${encodeURIComponent(motherCpid)}`,
  );
  return response.data.data ?? [];
}

export async function recordReproductiveIntention(params: {
  subjectCpid: string;
  intention: string;
  recordedBy: string;
}): Promise<ReproductiveIntention> {
  const response = await apiClient.post<{ data: ReproductiveIntention }>(
    `${REPRODUCTIVE_BASE}/reproductive-intentions`,
    params,
  );
  return response.data.data;
}

export async function openPreconceptionPlan(params: {
  subjectCpid: string;
  folicAcidStartedOn?: string;
  recordedBy: string;
}): Promise<PreconceptionPlan> {
  const response = await apiClient.post<{ data: PreconceptionPlan }>(
    `${REPRODUCTIVE_BASE}/preconception-plans`,
    params,
  );
  return response.data.data;
}

export async function startFertilityEpisode(params: {
  subjectCpid: string;
  monthsTrying?: number;
  recordedBy: string;
}): Promise<FertilityEpisode> {
  const response = await apiClient.post<{ data: FertilityEpisode }>(
    `${REPRODUCTIVE_BASE}/fertility-episodes`,
    params,
  );
  return response.data.data;
}

/**
 * Record a delivery on the confidential lane.
 *
 * A 409 (`DELIVERY_ALREADY_RECORDED`) propagates as an ApiError the caller must surface with the
 * reconciliation guidance — twins are one delivery with a higher babiesDelivered count, never a
 * second row.
 */
export async function recordDeliveryRecord(params: {
  motherCpid: string;
  pregnancyEpisodeId?: string;
  deliveredAt: string;
  deliveryMode: string;
  babiesDelivered?: number;
  recordedBy: string;
}): Promise<DeliveryRecord> {
  const response = await apiClient.post<{ data: DeliveryRecord }>(
    `${REPRODUCTIVE_BASE}/delivery-records`,
    params,
  );
  return response.data.data;
}
