/**
 * Pure summarising logic for the adult medicine workspace.
 *
 * Kept out of the components so the clinical rules here can be tested directly, the way
 * `paediatric-age.ts` / `due-today.ts` are for the paediatric workspace. Nothing in this file
 * fetches: it is given what was read and decides what to say about it.
 */

import type { ProgrammeEnrolment } from "@/hooks/queries/usePrograms";
import type { ConditionResource } from "@/hooks/queries/useConditions";

/** Statuses under which a person is still in the programme. Mirrors pct's OPEN_ENROLMENT_STATUSES. */
const OPEN_ENROLMENT_STATUSES = new Set([
  "SCREENING",
  "ENROLLED",
  "ON_TREATMENT",
  "TREATMENT_INTERRUPTED",
]);

/**
 * Clinical statuses that mean the problem is still live.
 *
 * RECURRENCE and RELAPSE are deliberately included: a returning disease is an active problem, and
 * the whole point of pct's V100 status widening was that a relapse must not read as a resolved
 * illness. REMISSION is excluded from "active" but is NOT the same as resolved, so it is surfaced
 * separately rather than folded into either bucket.
 */
const ACTIVE_PROBLEM_STATUSES = new Set(["ACTIVE", "RECURRENCE", "RELAPSE"]);

export const PROGRAMME_LABELS: Record<string, string> = {
  HIV_CARE: "HIV care",
  TB_TREATMENT: "TB treatment",
  HIV_PREVENTION: "HIV prevention (PrEP)",
  TB_PREVENTION: "TB preventive therapy",
};

export function programmeLabel(programme: string): string {
  return PROGRAMME_LABELS[programme] ?? programme;
}

/** Enrolments the person is still in, most recently enrolled first. */
export function openEnrolments(enrolments: ProgrammeEnrolment[]): ProgrammeEnrolment[] {
  return enrolments
    .filter((e) => OPEN_ENROLMENT_STATUSES.has(e.status))
    .sort((a, b) => (b.enrolled_on ?? "").localeCompare(a.enrolled_on ?? ""));
}

/**
 * Enrolments that have ended. Kept visible rather than hidden: a patient who was lost to follow-up
 * or transferred out is a clinically important fact, and a workspace that silently drops exited
 * enrolments would show the same empty programme list for "never enrolled" and "stopped coming".
 */
export function exitedEnrolments(enrolments: ProgrammeEnrolment[]): ProgrammeEnrolment[] {
  return enrolments.filter((e) => !OPEN_ENROLMENT_STATUSES.has(e.status));
}

export interface ProblemGroups {
  active: ConditionResource[];
  remission: ConditionResource[];
  inactive: ConditionResource[];
}

/**
 * Splits the problem list three ways.
 *
 * Three buckets rather than two because INACTIVE, REMISSION and RESOLVED are different clinical
 * claims and collapsing them loses the distinction the record exists to hold — the same reason the
 * SHR mapping refuses to fold them together.
 */
export function groupProblems(conditions: ConditionResource[]): ProblemGroups {
  const active: ConditionResource[] = [];
  const remission: ConditionResource[] = [];
  const inactive: ConditionResource[] = [];
  for (const c of conditions) {
    const status = (c.attributes.clinicalStatus ?? "").toUpperCase();
    if (ACTIVE_PROBLEM_STATUSES.has(status)) {
      active.push(c);
    } else if (status === "REMISSION") {
      remission.push(c);
    } else {
      inactive.push(c);
    }
  }
  return { active, remission, inactive };
}

/**
 * Whether a programme enrolment is confidential.
 *
 * The server sends `confidential` (pct derives it from the programme, so the two cannot drift).
 * This falls back to the HIV programmes only when the field is absent — an older payload must not
 * cause an HIV enrolment to render without its badge, because the badge is the cue a clinician
 * uses before discussing the record in an open room.
 */
export function isConfidential(enrolment: ProgrammeEnrolment): boolean {
  if (typeof enrolment.confidential === "boolean") {
    return enrolment.confidential;
  }
  return enrolment.programme === "HIV_CARE" || enrolment.programme === "HIV_PREVENTION";
}
