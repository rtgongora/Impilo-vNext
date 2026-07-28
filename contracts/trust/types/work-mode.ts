/**
 * WorkMode — the type of work being performed within a resolved work
 * context, alongside facility/role: "Facility Mode is the environment. Role
 * determines authority. Mode determines the type of work."
 *
 * TS twin of `libs/tshepo-contracts/.../enums/WorkMode.java` — values here
 * must exactly match the Java enum constant names (asserted by a golden-
 * thread test); this file carries no authorization of its own, only the
 * vocabulary and display metadata for the picker/switcher UI.
 *
 * District/Provincial/National management deliberately collapse into the
 * single JURISDICTION_OPERATIONS mode — the geographic level is carried by
 * the duty token's jurisdiction_code, not a separate mode per level.
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

export type WorkModeAnchorKind =
  | "FACILITY"
  | "ORGANISATION"
  | "JURISDICTION"
  | "PROGRAMME"
  | "FACILITY_OR_ORGANISATION";

export type WorkModeClinicalDataAccess = "IDENTIFIED" | "AGGREGATE" | "NONE";

export interface WorkModeDefinition {
  mode: WorkMode;
  label: string;
  anchorKind: WorkModeAnchorKind;
  clinicalDataAccess: WorkModeClinicalDataAccess;
}

/** Display/anchor metadata mirroring the Java enum's constructor arguments. Not authorization. */
export const WORK_MODE_DEFINITIONS: Readonly<Record<WorkMode, WorkModeDefinition>> = {
  CLINICAL_CARE: {
    mode: "CLINICAL_CARE",
    label: "Clinical Care",
    anchorKind: "FACILITY",
    clinicalDataAccess: "IDENTIFIED",
  },
  VIRTUAL_CARE: {
    mode: "VIRTUAL_CARE",
    label: "Virtual Care",
    anchorKind: "FACILITY_OR_ORGANISATION",
    clinicalDataAccess: "IDENTIFIED",
  },
  DEPARTMENT_MANAGEMENT: {
    mode: "DEPARTMENT_MANAGEMENT",
    label: "Department Management",
    anchorKind: "FACILITY",
    clinicalDataAccess: "AGGREGATE",
  },
  FACILITY_MANAGEMENT: {
    mode: "FACILITY_MANAGEMENT",
    label: "Facility Management",
    anchorKind: "FACILITY",
    clinicalDataAccess: "AGGREGATE",
  },
  JURISDICTION_OPERATIONS: {
    mode: "JURISDICTION_OPERATIONS",
    label: "Jurisdiction Operations",
    anchorKind: "JURISDICTION",
    clinicalDataAccess: "AGGREGATE",
  },
  PROGRAMME_MANAGEMENT: {
    mode: "PROGRAMME_MANAGEMENT",
    label: "Programme Management",
    anchorKind: "PROGRAMME",
    clinicalDataAccess: "AGGREGATE",
  },
  TECHNICAL_SUPPORT: {
    mode: "TECHNICAL_SUPPORT",
    label: "Technical Support",
    anchorKind: "ORGANISATION",
    clinicalDataAccess: "NONE",
  },
  FACILITY_SUPPORT: {
    mode: "FACILITY_SUPPORT",
    label: "Facility Support",
    anchorKind: "FACILITY",
    clinicalDataAccess: "NONE",
  },
  REGULATORY_OPERATIONS: {
    mode: "REGULATORY_OPERATIONS",
    label: "Regulatory Operations",
    anchorKind: "ORGANISATION",
    clinicalDataAccess: "NONE",
  },
  INSPECTION_COMPLIANCE: {
    mode: "INSPECTION_COMPLIANCE",
    label: "Inspection & Compliance",
    anchorKind: "ORGANISATION",
    clinicalDataAccess: "NONE",
  },
};

export function grantsIdentifiedClinicalRead(mode: WorkMode): boolean {
  return WORK_MODE_DEFINITIONS[mode].clinicalDataAccess === "IDENTIFIED";
}
