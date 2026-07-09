/**
 * Workforce Intake wizard state — pure helpers for the staged journey
 * (Upload → Mapping & validation → Match & dedupe → Confirm & approve →
 * Execution → Invitations & completion).
 *
 * Kept free of React so the step gating, header mapping suggestions and
 * stage→step derivation are unit-testable.
 */

export const WIZARD_STEPS = [
  "upload",
  "mapping",
  "match",
  "approve",
  "execute",
  "complete",
] as const;

export type WizardStep = (typeof WIZARD_STEPS)[number];

export const STEP_LABELS: Record<WizardStep, string> = {
  upload: "Upload staff CSV",
  mapping: "Mapping & validation",
  match: "Match & dedupe preview",
  approve: "Confirm & approve",
  execute: "Execution progress",
  complete: "Invitations & completion",
};

/** Canonical template columns (lower-cased — the CSV parser lower-cases headers). */
export const CANONICAL_COLUMNS = [
  "nationalid",
  "givenname",
  "familyname",
  "profession",
  "cadre",
  "councilcode",
  "councilnumber",
  "facilitycode",
  "email",
  "phone",
  "healthid",
] as const;

export const REQUIRED_COLUMNS = ["nationalid", "givenname", "familyname", "profession"] as const;

/** Derives the wizard step to land on from the batch's server-side stage. */
export function stepForStage(stage: string | undefined | null): WizardStep {
  switch ((stage ?? "").toUpperCase()) {
    case "UPLOADED":
    case "MAPPED":
      return "mapping";
    case "VALIDATED":
      return "match";
    case "MATCHED":
      return "approve";
    case "APPROVED":
    case "EXECUTING":
    case "PARTIAL":
      return "execute";
    case "COMPLETED":
      return "complete";
    default:
      return "upload";
  }
}

export function stepIndex(step: WizardStep): number {
  return WIZARD_STEPS.indexOf(step);
}

/** Parses just the header row of a CSV (lower-cased, trimmed) without touching row data. */
export function parseCsvHeaders(csvContent: string): string[] {
  const firstLine = csvContent.replace(/\r/g, "").split("\n")[0] ?? "";
  if (!firstLine.trim()) return [];
  return firstLine.split(",").map((header) => header.trim().toLowerCase()).filter(Boolean);
}

export function countCsvDataRows(csvContent: string): number {
  const lines = csvContent.replace(/\r/g, "").split("\n").filter((line) => line.trim().length > 0);
  return Math.max(0, lines.length - 1);
}

/**
 * Suggests a source-header → canonical-column mapping. Exact canonical headers
 * map to themselves; a small alias table covers common HR-export spellings.
 * Unknown headers stay unmapped (they pass through as-is server-side).
 */
export function suggestMapping(sourceHeaders: string[]): Record<string, string> {
  const aliases: Record<string, string> = {
    national_id: "nationalid",
    id_number: "nationalid",
    idnumber: "nationalid",
    nid: "nationalid",
    given_name: "givenname",
    first_name: "givenname",
    firstname: "givenname",
    forename: "givenname",
    family_name: "familyname",
    last_name: "familyname",
    lastname: "familyname",
    surname: "familyname",
    council_code: "councilcode",
    council: "councilcode",
    council_number: "councilnumber",
    registration_number: "councilnumber",
    reg_number: "councilnumber",
    facility_code: "facilitycode",
    facility: "facilitycode",
    duty_station: "facilitycode",
    email_address: "email",
    official_email: "email",
    phone_number: "phone",
    mobile: "phone",
    msisdn: "phone",
    health_id: "healthid",
    cadre_code: "cadre",
    post_title: "profession",
    job_title: "profession",
  };
  const mapping: Record<string, string> = {};
  for (const header of sourceHeaders) {
    if ((CANONICAL_COLUMNS as readonly string[]).includes(header)) {
      mapping[header] = header;
    } else if (aliases[header]) {
      mapping[header] = aliases[header];
    }
  }
  return mapping;
}

/** Required canonical columns not covered by the current mapping's targets. */
export function missingRequiredColumns(mapping: Record<string, string>): string[] {
  const mapped = new Set(Object.values(mapping));
  return REQUIRED_COLUMNS.filter((column) => !mapped.has(column));
}

/** A batch can only be executed from APPROVED (fresh) or PARTIAL (resume). */
export function canExecute(stage: string | undefined | null): boolean {
  const s = (stage ?? "").toUpperCase();
  return s === "APPROVED" || s === "PARTIAL";
}

/** Terminal per-row execution statuses that count as journey-complete for that row. */
export function isRowSettled(executionStatus: string | undefined | null): boolean {
  if (!executionStatus) return false;
  return (
    executionStatus === "DONE" ||
    executionStatus === "BLOCKED_MISSING_CONTACT" ||
    executionStatus.startsWith("SKIPPED_")
  );
}
