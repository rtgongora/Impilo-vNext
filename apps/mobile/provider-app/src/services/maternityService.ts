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
