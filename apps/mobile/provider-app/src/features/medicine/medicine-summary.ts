/**
 * Pure summarising logic for the adult medicine workspace (mobile).
 *
 * Mirrors ui/one-ui-shell/src/features/medicine/workspace/medicine-summary.ts so clinical
 * grouping rules stay aligned with web. Nothing here fetches — callers pass what was read.
 */

import type { ConditionResource, ProgrammeEnrolment } from "../../services/medicineService";

const OPEN_ENROLMENT_STATUSES = new Set([
  "SCREENING",
  "ENROLLED",
  "ON_TREATMENT",
  "TREATMENT_INTERRUPTED",
]);

const ACTIVE_PROBLEM_STATUSES = new Set(["ACTIVE", "RECURRENCE", "RELAPSE"]);

export const PROGRAMME_LABELS: Record<string, string> = {
  HIV_CARE: "HIV care",
  TB_TREATMENT: "TB treatment",
  HIV_PREVENTION: "HIV prevention (PrEP)",
  TB_PREVENTION: "TB preventive therapy",
  HYPERTENSION: "Hypertension",
  DIABETES: "Diabetes",
  CHRONIC_KIDNEY_DISEASE: "Chronic kidney disease",
  CHRONIC_RESPIRATORY: "Asthma and COPD",
};

export function programmeLabel(programme: string): string {
  return PROGRAMME_LABELS[programme] ?? programme;
}

export function openEnrolments(enrolments: ProgrammeEnrolment[]): ProgrammeEnrolment[] {
  return enrolments
    .filter((e) => OPEN_ENROLMENT_STATUSES.has(e.status))
    .sort((a, b) => (b.enrolled_on ?? "").localeCompare(a.enrolled_on ?? ""));
}

export function exitedEnrolments(enrolments: ProgrammeEnrolment[]): ProgrammeEnrolment[] {
  return enrolments.filter((e) => !OPEN_ENROLMENT_STATUSES.has(e.status));
}

export interface ProblemGroups {
  active: ConditionResource[];
  remission: ConditionResource[];
  inactive: ConditionResource[];
}

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

export function isConfidential(enrolment: ProgrammeEnrolment): boolean {
  if (typeof enrolment.confidential === "boolean") {
    return enrolment.confidential;
  }
  return enrolment.programme === "HIV_CARE" || enrolment.programme === "HIV_PREVENTION";
}

export function allergyLabelsFromResources(
  entries: Array<{ attributes?: Record<string, unknown> }>,
): string[] {
  return entries
    .map((entry) => {
      const attrs = entry.attributes;
      const allergen = attrs?.allergen ?? attrs?.substance;
      return typeof allergen === "string" ? allergen : null;
    })
    .filter((label): label is string => Boolean(label));
}
