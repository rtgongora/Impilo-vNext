/**
 * WHO DAK–aligned structured clinical form model (software-neutral pattern).
 * Form definitions are data; the renderer interprets them with patient / role / encounter context.
 */

export type ClinicalFieldKind =
  | "coded_single"
  | "coded_multi"
  | "numeric_with_unit"
  | "date"
  | "datetime"
  | "boolean"
  | "segmented"
  | "terminology_bound"
  | "fhir_mapped"
  | "text"
  /**
   * Yes / No / Unknown / Not assessed. A clinical record has to distinguish "no" from
   * "nobody looked": a danger sign left blank is not a danger sign ruled out, and the
   * decision-support engines refuse to treat the two alike. Options are supplied by the
   * renderer from the canonical set, so the same answer means the same thing everywhere.
   */
  | "clinical_assertion"
  /**
   * Normal / Abnormal / Not examined / Unable / Deferred / Child distressed / Uncertain.
   * A blank examination field must never read as normal, and the reasons an examination
   * produced no finding lead to different next steps.
   */
  | "exam_finding"
  /**
   * An interactive anatomical map. The value is the marked-finding list itself, so
   * examination findings ride the ordinary form-response spine rather than a parallel
   * store that could drift out of sync with the encounter. The instrument is named by
   * {@link ClinicalFormFieldDefinition.bodyMapInstrument}.
   */
  | "body_map";

export type AgeBand = "NEONATAL" | "INFANT" | "CHILD" | "ADOLESCENT" | "ADULT" | "OLDER_ADULT";

export type Sex = "MALE" | "FEMALE" | "UNKNOWN" | "OTHER";

export interface ClinicalFormPatientContext {
  patientId: string;
  /** Whole months since DOB (floor). */
  ageMonths: number;
  ageBand: AgeBand;
  sex: Sex;
  /** null = unknown; true/false when documented */
  pregnant: boolean | null;
  programmes: string[];
  knownConditionCodes: string[];
  medicationFlags?: { onArt?: boolean };
  allergySeverities?: string[];
}

export interface ClinicalFormRuntimeContext {
  patient: ClinicalFormPatientContext;
  encounterType: string;
  providerRoles: string[];
  facilityType?: string | null;
  workspaceKind?: string | null;
}

export interface FieldVisibilityRule {
  /** Field is shown only if patient sex is in this list (omit = any). */
  requireSex?: Sex[];
  /** Hidden if sex is in this list. */
  excludeSex?: Sex[];
  requireAgeBands?: AgeBand[];
  excludeAgeBands?: AgeBand[];
  /** If true, show only when pregnant === true. If false, show only when pregnant !== true. */
  requirePregnant?: boolean;
  requireProgrammes?: string[];
  excludeProgrammes?: string[];
  requireEncounterTypes?: string[];
  excludeEncounterTypes?: string[];
  /** At least one role must match (Keycloak-style role strings, without ROLE_ prefix). */
  requireAnyRole?: string[];
  excludeRoles?: string[];
}

export interface CodedOption {
  code: string;
  display: string;
  /** SNOMED CT / ICD / local extension */
  terminologyCode?: string;
  terminologySystem?: string;
}

export interface FieldValidationRule {
  required?: boolean;
  min?: number;
  max?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  /** When outside plausible range, require free-text justification */
  requireOverrideReason?: boolean;
}

export interface FhirFieldLink {
  questionnaireItemLinkId: string;
  /** FHIRpath or logical element id for future profiling */
  fhirElementId?: string;
}

export interface IndicatorLink {
  programmeIndicatorCode: string;
  disaggregation?: string;
}

export interface DecisionSupportHookRef {
  id: string;
  /** Future: rules-service / CDS endpoint id */
  engine: "local_placeholder" | "rules_service" | "vito_service";
}

export interface ClinicalFormFieldDefinition {
  id: string;
  linkId: string;
  label: string;
  description?: string;
  kind: ClinicalFieldKind;
  visibility?: FieldVisibilityRule;
  validation?: FieldValidationRule;
  /** For coded / segmented / multi */
  options?: CodedOption[];
  /** UCUM or display unit */
  unit?: string;
  /** Primary terminology binding for this field (e.g. LOINC observation code) */
  terminologyBinding?: { system: string; code: string; display?: string };
  /** Free text only when clinically justified */
  allowAuxiliaryNarrative?: boolean;
  auxiliaryNarrativeLabel?: string;
  fhir?: FhirFieldLink;
  indicators?: IndicatorLink[];
  decisionHooks?: DecisionSupportHookRef[];
  /** Group within section */
  groupId?: string;
  /** For kind "body_map": which examination instrument to render (see features/body-map). */
  bodyMapInstrument?: string;
}

export interface ClinicalFormSectionDefinition {
  id: string;
  title: string;
  description?: string;
  fields: ClinicalFormFieldDefinition[];
}

export interface ClinicalFormDefinition {
  id: string;
  version: string;
  title: string;
  description: string;
  /** WHO guideline / DAK identifier (documentation anchor, not a full DAK bundle). */
  dakReference?: string;
  healthDomain: string;
  programme: string;
  encounterTypes: string[];
  /** Minimum age in months (inclusive) */
  minAgeMonths?: number;
  maxAgeMonths?: number;
  sexApplicability?: Sex[] | "ALL";
  providerRoles?: string[];
  facilityTypes?: string[];
  workspaceKinds?: string[];
  sections: ClinicalFormSectionDefinition[];
  localization?: { defaultLocale: string; supportedLocales?: string[] };
  offlineCapable?: boolean;
  audit: {
    dataCustodian: string;
    sensitivity: "STANDARD" | "SENSITIVE" | "RESTRICTED";
  };
}

/**
 * A body-map finding as carried in form values. Structurally mirrors
 * {@code features/body-map/core/types.ts MarkedRegionFinding}; declared here so the form
 * value union stays self-contained and JSON-serialisable for drafts and submission.
 */
export interface FormBodyMapFinding {
  regionId: string;
  locationCode: { system: string; code: string; display?: string };
  laterality?: "left" | "right" | "midline";
  morphology?: string;
  severity?: "mild" | "moderate" | "severe";
  note?: string;
  onset?: string;
  photoConsent?: "granted" | "declined" | "not_asked";
}

export type FormValues = Record<
  string,
  string | number | boolean | string[] | FormBodyMapFinding[] | null | undefined
>;

export interface FormValidationIssue {
  fieldId: string;
  message: string;
  /**
   * "error" blocks submission; "warning" surfaces a gap without blocking. An unassessed
   * danger sign is a warning: recording that it was not assessed is honest and must stay
   * possible, but the clinician should see that the question is still open.
   */
  severity?: "error" | "warning";
}
