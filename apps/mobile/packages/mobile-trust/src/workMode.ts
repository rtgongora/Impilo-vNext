/**
 * Impilo vNext — Mobile Work Mode Contract
 *
 * Hand-mirror of contracts/trust/types/work-mode.ts (itself the TS twin of
 * libs/tshepo-contracts/.../enums/WorkMode.java). This is the pragmatic G1
 * choice, not the target end-state: G2 is expected to give mobile a real
 * shared-package bridge into contracts/trust (apps/mobile is its own pnpm
 * workspace and cannot reach contracts/trust via a relative import the way
 * ui/one-ui-shell does), at which point this file should be deleted in favour
 * of the shared import. Until then, keep every value here byte-identical to
 * contracts/trust/types/work-mode.ts — there is no golden-thread test
 * enforcing that yet (unlike ~60 web-side tests that compare against the Java
 * enum), so a manual diff is required whenever WorkMode changes upstream.
 */

export type WorkMode =
  | "CLINICAL_CARE"
  | "VIRTUAL_CARE"
  | "DEPARTMENT_MANAGEMENT"
  | "FACILITY_MANAGEMENT"
  | "JURISDICTION_OPERATIONS"
  | "PROGRAMME_MANAGEMENT"
  | "TECHNICAL_SUPPORT"
  | "FACILITY_SUPPORT"
  | "REGULATORY_OPERATIONS"
  | "INSPECTION_COMPLIANCE";

export const WORK_MODES: readonly WorkMode[] = [
  "CLINICAL_CARE",
  "VIRTUAL_CARE",
  "DEPARTMENT_MANAGEMENT",
  "FACILITY_MANAGEMENT",
  "JURISDICTION_OPERATIONS",
  "PROGRAMME_MANAGEMENT",
  "TECHNICAL_SUPPORT",
  "FACILITY_SUPPORT",
  "REGULATORY_OPERATIONS",
  "INSPECTION_COMPLIANCE",
];

export type WorkModeAnchorKind = "FACILITY" | "ORGANISATION" | "JURISDICTION" | "PROGRAMME" | "FACILITY_OR_ORGANISATION";

export type WorkModeClinicalDataAccess = "IDENTIFIED" | "AGGREGATE" | "NONE";

export interface WorkModeDefinition {
  mode: WorkMode;
  label: string;
  anchorKind: WorkModeAnchorKind;
  clinicalDataAccess: WorkModeClinicalDataAccess;
}

export const WORK_MODE_DEFINITIONS: Readonly<Record<WorkMode, WorkModeDefinition>> = {
  CLINICAL_CARE: { mode: "CLINICAL_CARE", label: "Clinical Care", anchorKind: "FACILITY", clinicalDataAccess: "IDENTIFIED" },
  VIRTUAL_CARE: { mode: "VIRTUAL_CARE", label: "Virtual Care", anchorKind: "FACILITY_OR_ORGANISATION", clinicalDataAccess: "IDENTIFIED" },
  DEPARTMENT_MANAGEMENT: { mode: "DEPARTMENT_MANAGEMENT", label: "Department Management", anchorKind: "FACILITY", clinicalDataAccess: "AGGREGATE" },
  FACILITY_MANAGEMENT: { mode: "FACILITY_MANAGEMENT", label: "Facility Management", anchorKind: "FACILITY", clinicalDataAccess: "AGGREGATE" },
  JURISDICTION_OPERATIONS: { mode: "JURISDICTION_OPERATIONS", label: "Jurisdiction Operations", anchorKind: "JURISDICTION", clinicalDataAccess: "AGGREGATE" },
  PROGRAMME_MANAGEMENT: { mode: "PROGRAMME_MANAGEMENT", label: "Programme Management", anchorKind: "PROGRAMME", clinicalDataAccess: "AGGREGATE" },
  TECHNICAL_SUPPORT: { mode: "TECHNICAL_SUPPORT", label: "Technical Support", anchorKind: "ORGANISATION", clinicalDataAccess: "NONE" },
  FACILITY_SUPPORT: { mode: "FACILITY_SUPPORT", label: "Facility Support", anchorKind: "FACILITY", clinicalDataAccess: "NONE" },
  REGULATORY_OPERATIONS: { mode: "REGULATORY_OPERATIONS", label: "Regulatory Operations", anchorKind: "ORGANISATION", clinicalDataAccess: "NONE" },
  INSPECTION_COMPLIANCE: { mode: "INSPECTION_COMPLIANCE", label: "Inspection & Compliance", anchorKind: "ORGANISATION", clinicalDataAccess: "NONE" },
};

export function grantsIdentifiedClinicalRead(mode: WorkMode): boolean {
  return WORK_MODE_DEFINITIONS[mode].clinicalDataAccess === "IDENTIFIED";
}

/**
 * A single resolved work context as returned by
 * `GET /internal/v1/work-context/resolved` (mirrors experience-bff's
 * WorkContextController#toContextView / ResolvedWorkContext.java field-for-field).
 */
export interface ResolvedWorkContextView {
  contextId: string;
  contextKind: "facility" | "organisation" | "jurisdiction" | "programme" | "regulator" | "virtual" | "support" | string;
  sourceSystem: "VASHANDI" | "WGV" | "ORG_REGISTRY" | "TUSO" | "SUPPORT" | string;
  facilityId?: string | null;
  organisationId?: string | null;
  jurisdictionCode?: string | null;
  programmeId?: string | null;
  departmentId?: string | null;
  roleTemplateId?: string | null;
  availableModes: WorkMode[];
  defaultMode?: WorkMode | null;
  modeSource?: "CATALOG" | "DERIVED_REMOTE" | "DERIVED_LOCAL" | string;
  restrictions: string[];
  label: string;
  groupHint: "today" | "regular" | "virtual" | "oversight" | "other" | "personal" | string;
  rankScore?: number;
}

export interface WorkContextSourceStatus {
  system: string;
  state: "LIVE" | "EMPTY" | "DEGRADED" | string;
  message?: string;
}

export interface ResolvedWorkContextsAttributes {
  contexts: ResolvedWorkContextView[];
  sourceStatuses: WorkContextSourceStatus[];
  recommendedContextId?: string | null;
  requiresContextChooser: boolean;
  friendlyResolutionState?: string;
}

/** Attributes of the `work-mode-session` resource returned by session/mode mint. */
export interface WorkModeSessionAttributes {
  token?: string;
  jti?: string;
  expiresAt?: string | null;
  contextId?: string;
  workMode?: string;
}
