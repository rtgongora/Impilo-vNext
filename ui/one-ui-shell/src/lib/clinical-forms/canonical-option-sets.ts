import type { CodedOption } from "./types";

/**
 * Canonical answer sets for the two clinical field kinds whose options should never vary
 * between forms.
 *
 * A clinical record has to distinguish "no" from "nobody looked". Until now the form model
 * had no way to express that: a danger sign left blank was indistinguishable from a danger
 * sign explicitly ruled out, and both read downstream as absent. That is the failure the
 * decision-support engines were built to refuse, and it has to be expressible at the point
 * of capture or the engine never sees the difference.
 *
 * These sets are fixed rather than author-supplied so that the same answer means the same
 * thing in every form, and so downstream classification can rely on the codes.
 */

/** SNOMED CT codes for the qualifier values, so answers are interoperable, not just local. */
const SNOMED = "http://snomed.info/sct";

/**
 * Yes / No / Unknown / Not assessed.
 *
 * "Unknown" and "Not assessed" are different clinical statements and are kept apart: unknown
 * means the question was asked and could not be answered (the caregiver does not know);
 * not assessed means it was never asked. The first is a fact about the child, the second is
 * a fact about the consultation, and only the second is a gap a clinician can close.
 */
export const CLINICAL_ASSERTION_OPTIONS: CodedOption[] = [
  { code: "yes", display: "Yes", terminologyCode: "373066001", terminologySystem: SNOMED },
  { code: "no", display: "No", terminologyCode: "373067005", terminologySystem: SNOMED },
  { code: "unknown", display: "Unknown", terminologyCode: "261665006", terminologySystem: SNOMED },
  { code: "not_assessed", display: "Not assessed", terminologyCode: "not-assessed", terminologySystem: SNOMED },
];

/**
 * Examination finding states.
 *
 * A blank examination field must never be read as normal. These states separate the reasons
 * an examination produced no finding, because they lead to different next steps: a deferred
 * examination should be completed later, a child too distressed to examine may settle, and
 * an uncertain finding needs a second opinion. Collapsing them into "not documented" loses
 * all of that.
 */
export const EXAM_FINDING_OPTIONS: CodedOption[] = [
  { code: "normal", display: "Normal", terminologyCode: "17621005", terminologySystem: SNOMED },
  { code: "abnormal", display: "Abnormal", terminologyCode: "263654008", terminologySystem: SNOMED },
  { code: "not_examined", display: "Not examined", terminologyCode: "not-examined", terminologySystem: SNOMED },
  { code: "unable", display: "Unable to examine", terminologyCode: "unable-to-examine", terminologySystem: SNOMED },
  { code: "deferred", display: "Deferred", terminologyCode: "deferred", terminologySystem: SNOMED },
  { code: "distressed", display: "Child distressed", terminologyCode: "child-distressed", terminologySystem: SNOMED },
  { code: "uncertain", display: "Uncertain", terminologyCode: "uncertain", terminologySystem: SNOMED },
];

/** Codes that mean an answer was recorded, as opposed to the question being left open. */
const ANSWERED_ASSERTIONS = new Set(["yes", "no", "unknown"]);
const ANSWERED_EXAM_FINDINGS = new Set(["normal", "abnormal", "unable", "distressed", "uncertain"]);

/**
 * True when the value states a clinical finding rather than recording that nothing was
 * looked at. Used by validation so a form can require that a question was addressed without
 * requiring a particular answer.
 */
export function isAnsweredAssertion(value: unknown): boolean {
  return typeof value === "string" && ANSWERED_ASSERTIONS.has(value);
}

export function isAnsweredExamFinding(value: unknown): boolean {
  return typeof value === "string" && ANSWERED_EXAM_FINDINGS.has(value);
}

/**
 * True when a value positively asserts the finding is present. Deliberately narrow:
 * "unknown" and "not assessed" are not negatives, so this returns false for them and callers
 * must check {@link isAnsweredAssertion} before treating a false as reassurance.
 */
export function assertsPresent(value: unknown): boolean {
  return value === "yes";
}

/** Tones used when these sets are rendered, so severity reads at a glance. */
export const CLINICAL_ASSERTION_TONES: Record<string, "default" | "positive" | "warning" | "danger" | "muted"> = {
  yes: "danger",
  no: "positive",
  unknown: "warning",
  not_assessed: "muted",
};

export const EXAM_FINDING_TONES: Record<string, "default" | "positive" | "warning" | "danger" | "muted"> = {
  normal: "positive",
  abnormal: "danger",
  not_examined: "muted",
  unable: "muted",
  deferred: "warning",
  distressed: "warning",
  uncertain: "warning",
};

export function optionsForKind(kind: string): CodedOption[] | undefined {
  if (kind === "clinical_assertion") return CLINICAL_ASSERTION_OPTIONS;
  if (kind === "exam_finding") return EXAM_FINDING_OPTIONS;
  return undefined;
}

export function tonesForKind(kind: string): Record<string, "default" | "positive" | "warning" | "danger" | "muted"> {
  if (kind === "clinical_assertion") return CLINICAL_ASSERTION_TONES;
  if (kind === "exam_finding") return EXAM_FINDING_TONES;
  return {};
}
