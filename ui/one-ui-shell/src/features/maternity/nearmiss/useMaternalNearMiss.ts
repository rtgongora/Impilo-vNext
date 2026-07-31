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
