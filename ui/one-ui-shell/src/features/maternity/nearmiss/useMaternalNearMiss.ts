/**
 * Maternal near-miss — web hook.
 *
 * Backend: experience-bff `POST /internal/v1/clinical/maternal/near-miss/classify-form`, which
 * translates form-21 (`impilo.maternal.nearmiss.assessment.v1`) answers into the WHO organ-dysfunction
 * observation set and forwards them to CKP's classification engine. See
 * `MaternalNearMissController.java` for the exact contract this hook has to honour:
 *
 *   1. Blank ≠ ABSENT ≠ unrecognised. A field the clinician left blank is omitted from the request
 *      (never sent as "ABSENT") so its criterion stays unresolved on the server; an unrecognised
 *      answer is refused with a 422 naming which one, and that message must reach the clinician,
 *      never be swallowed into a generic "request failed".
 *   2. INDETERMINATE (or a CLASSIFIED-but-`provisional` outcome) must never render as "no near-miss".
 *      The BFF's own NOT_MET row already says so explicitly in its rationale; this hook does not
 *      collapse the two.
 *   3. A 502 (`near_miss_unavailable`, or a CKP-side `near_miss_criteria_unavailable` surfaced through
 *      the BFF's generic upstream-error passthrough) is an outage, not a clinical finding. Nothing
 *      here maps it to NOT_MET.
 *
 * Not on the confidential lane — see the controller's doc comment for why (near-miss identification
 * carries no confidentiality stamp; only the separate MPDSR review does).
 */
"use client";

import { useMemo } from "react";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useFormCatalog } from "@/hooks/queries/useEncounterForms";

export const NEAR_MISS_FORM_KEY = "impilo.maternal.nearmiss.assessment.v1";

export interface NearMissFormOption {
  code: string;
  display: string;
}

export interface NearMissFormField {
  id: string;
  linkId: string;
  label: string;
  kind: string;
  unit?: string;
  description?: string;
  options?: NearMissFormOption[];
  validation?: { required?: boolean };
}

export interface NearMissFormSection {
  id: string;
  title: string;
  description?: string;
  fields: NearMissFormField[];
}

export interface NearMissFormDefinition {
  id: string;
  title: string;
  description?: string;
  sections: NearMissFormSection[];
}

/** Loads form 21 from the shared clinical form catalog (same catalog partograph/CTG resolve from). */
export function useNearMissFormDefinition() {
  const catalog = useFormCatalog();

  const entry = catalog.data?.data.find((f) => f.formKey === NEAR_MISS_FORM_KEY);

  const definition = useMemo<NearMissFormDefinition | null>(() => {
    if (!entry?.definitionJson) return null;
    try {
      return JSON.parse(entry.definitionJson) as NearMissFormDefinition;
    } catch {
      return null;
    }
  }, [entry]);

  return {
    isLoading: catalog.isLoading,
    isError: catalog.isError,
    definition,
    formSchemaVersionId: entry?.formSchemaVersionId,
  };
}

/**
 * The classification outcome, exactly as `MaternalNearMissController#classifyFromForm` composes it
 * from CKP's `data` object. `status` is one of the engine's four states — CLASSIFIED, NOT_APPLICABLE,
 * NOT_ASSESSED, INDETERMINATE — and only a CLASSIFIED outcome with `provisional === false` is a
 * finished answer; every other combination has open work.
 */
export interface NearMissClassification {
  classification_code: string | null;
  classification_name: string | null;
  status: string;
  is_near_miss: boolean;
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
  /** Bare field names (no `nearMiss.` prefix) the clinician left blank — named, not just counted. */
  criteria_left_blank?: string[];
}

export interface NearMissClassifyResponse {
  data: NearMissClassification;
  meta: NearMissClassifyMeta;
}

/**
 * A refused submission — the BFF's `unrecognised_form_answer` 422, or its `near_miss_unavailable` /
 * upstream 502. Both arrive as a flat `{ error: <string code>, message, ... }` body (see the
 * controller), not the nested `{ error: { code, message } }` envelope most BFF surfaces use, so this
 * type mirrors what actually arrives on the wire rather than the more common shape.
 */
export interface NearMissRefusal {
  status: number;
  error?: string;
  message?: string;
  unrecognised_answers?: string[];
  accepted_codes?: string[];
  upstream_status?: number;
  upstream_body?: string;
}

export function isNearMissRefusal(err: unknown): err is NearMissRefusal {
  return typeof err === "object" && err !== null && "status" in err;
}

/** True for the 422 naming an answer the BFF could not read (see `MaternalNearMissController`). */
export function isUnrecognisedAnswerRefusal(err: unknown): boolean {
  return isNearMissRefusal(err) && err.status === 422 && err.error === "unrecognised_form_answer";
}

/**
 * True for any failure to reach the classification service — the transport-level
 * `near_miss_unavailable` 502 and any other 5xx. Callers must render this distinctly from
 * NOT_MET: it is a statement that nothing was evaluated, never that nothing was found.
 */
export function isNearMissUnavailable(err: unknown): boolean {
  return isNearMissRefusal(err) && err.status >= 500;
}

/**
 * Classifies a woman from completed form-21 answers.
 *
 * `answers` should be exactly the linkId → value map the form collected — including omitting a
 * field the clinician left blank. This hook adds nothing: filling in a default here would be the
 * same false negative the BFF controller exists to refuse.
 */
export function useClassifyMaternalNearMiss() {
  return useMutation({
    mutationFn: (answers: Record<string, unknown>) =>
      apiClient.post<NearMissClassifyResponse>(
        "/internal/v1/clinical/maternal/near-miss/classify-form",
        { formKey: NEAR_MISS_FORM_KEY, answers },
      ),
  });
}

// ── Severe maternal outcome indicators ─────────────────────────────────────────────────────────
//
// Backend: `POST /internal/v1/clinical/maternal/near-miss/indicators`, a thin BFF proxy
// (`MaternalNearMissController#indicators`) straight through to CKP's `IndicatorEngine` — see
// `MaternalNearMissController.java` (CKP side) for the ratio/index math. Two laws this hook and its
// callers must not break:
//
//   1. An unrecognised or absent case outcome is CKP's 422 (`unclassifiable_outcome`), forwarded
//      intact by the BFF's generic proxy. Unlike `classify-form`'s refusal (a flat `{error,message}`
//      the BFF builds itself), the indicators proxy wraps CKP's whole response as
//      `{ upstream_status, upstream_body, meta }` — `upstream_body` is CKP's own
//      `{"data":{"error":"unclassifiable_outcome",...}}` response, serialised to a string, and must
//      be parsed to recover which case was rejected. See `parseIndicatorsRejection` below.
//   2. `near_miss_to_death_ratio` and `mortality_index` are `null` when there are zero deaths /
//      zero severe outcomes — the ratio is mathematically undefined, not zero. A null ratio must
//      never render as "0.00", and the indeterminate count must never be dropped from the counts
//      display just because it does not feed the ratio's numerator or denominator.

export type NearMissIndicatorOutcome =
  | "NEAR_MISS"
  | "MATERNAL_DEATH"
  | "NEAR_MISS_INDETERMINATE"
  | "NOT_SEVERE";

export interface NearMissIndicatorCase {
  outcome: NearMissIndicatorOutcome;
}

export interface NearMissIndicatorsRequest {
  indicatorPeriod: string;
  cases: NearMissIndicatorCase[];
}

/** Exactly the CKP `IndicatorEngine`'s field set, as forwarded by the BFF untouched. */
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

export interface NearMissIndicatorsResponse {
  data: NearMissIndicatorsResult;
  meta: Record<string, unknown>;
}

/**
 * The indicators proxy's refusal shape — distinct from `NearMissRefusal` because the BFF's generic
 * `proxy()` helper (not the classify-form-specific 422 builder) produced it. A 502 still carries the
 * familiar flat `{error: "near_miss_unavailable", message}`; a 422 does not — see
 * `parseIndicatorsRejection`.
 */
export interface NearMissIndicatorsRefusal {
  status: number;
  error?: string;
  message?: string;
  upstream_status?: number;
  upstream_body?: string;
}

export function isIndicatorsRefusal(err: unknown): err is NearMissIndicatorsRefusal {
  return typeof err === "object" && err !== null && "status" in err;
}

/** True for a transport-level failure to reach CKP — an outage, not "nothing was submitted". */
export function isIndicatorsUnavailable(err: unknown): boolean {
  return isIndicatorsRefusal(err) && err.status >= 500;
}

/** True for CKP's 422 — one or more cases carried an outcome value nobody can count. */
export function isIndicatorsRejected(err: unknown): boolean {
  return isIndicatorsRefusal(err) && err.status === 422;
}

export interface ParsedIndicatorsRejection {
  message: string;
  rejectedCases: string[];
  acceptedValues: string[];
}

/**
 * Recovers CKP's actual refusal — which case, and what values it would have accepted — from the
 * BFF's forwarded `upstream_body` string. Returns `null` (never a fabricated rejection) when the
 * body is missing or not in the expected shape, so a caller falls back to a generic message rather
 * than inventing case details that were never in the response.
 */
export function parseIndicatorsRejection(err: NearMissIndicatorsRefusal): ParsedIndicatorsRejection | null {
  if (!err.upstream_body) return null;
  try {
    const parsed = JSON.parse(err.upstream_body) as { data?: Record<string, unknown> };
    const data = parsed.data;
    if (!data) return null;
    return {
      message:
        typeof data.message === "string"
          ? data.message
          : "Some cases carried an outcome that could not be classified.",
      rejectedCases: Array.isArray(data.rejected_cases) ? (data.rejected_cases as string[]) : [],
      acceptedValues: Array.isArray(data.accepted_values) ? (data.accepted_values as string[]) : [],
    };
  } catch {
    return null;
  }
}

/**
 * Computes the near-miss-to-death ratio and mortality index for a reporting period's cases.
 *
 * `cases` is forwarded exactly as given — this hook invents no default outcome for an unlabelled
 * case, because the only two "reasonable" defaults (NOT_SEVERE or NEAR_MISS_INDETERMINATE) are
 * exactly the two failure modes the CKP engine's own 422 exists to prevent a caller from committing.
 */
export function useNearMissIndicators() {
  return useMutation({
    mutationFn: (request: NearMissIndicatorsRequest) =>
      apiClient.post<NearMissIndicatorsResponse>(
        "/internal/v1/clinical/maternal/near-miss/indicators",
        request,
      ),
  });
}
