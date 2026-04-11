/**
 * Healthcare Coding Standards — TypeScript Contract
 *
 * Canonical type definitions for international coding standards used across
 * the Health Operating System. These types mirror the Java shared-kernel
 * models in `libs/shared-kernel-java/.../terminology/`.
 *
 * See: docs/doctrine/healthcare-coding-standards.md
 */

// ── Coding System URIs ──────────────────────────────────────────────

/**
 * Canonical URIs for adopted coding systems.
 * Must match CodingSystem.java enum values.
 */
export const CODING_SYSTEM = {
  // Primary international standards
  ICD_11: "http://id.who.int/icd/release/11",
  ICD_10: "http://hl7.org/fhir/sid/icd-10",
  SNOMED_CT: "http://snomed.info/sct",
  LOINC: "http://loinc.org",
  ATC: "http://www.whocc.no/atc",
  DICOM: "http://dicom.nema.org/resources/ontology/DCM",

  // Supplementary standards
  UCUM: "http://unitsofmeasure.org",
  CVX: "http://hl7.org/fhir/sid/cvx",
  CPT: "http://www.ama-assn.org/go/cpt",
  RXNORM: "http://www.nlm.nih.gov/research/umls/rxnorm",
  ISO_3166: "urn:iso:std:iso:3166",

  // HL7 vocabulary
  HL7_ACT_CODE: "http://terminology.hl7.org/CodeSystem/v3-ActCode",
  HL7_ROUTE: "http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
  HL7_OBSERVATION_CATEGORY:
    "http://terminology.hl7.org/CodeSystem/observation-category",

  // Impilo local extensions
  IMPILO_LOCAL: "http://impilo.gov.zw/coding",
  IMPILO_FACILITY: "urn:zw:mohcc:facility",
  IMPILO_ITEM: "urn:zw:mohcc:item",
} as const;

export type CodingSystemUri =
  (typeof CODING_SYSTEM)[keyof typeof CODING_SYSTEM];

// ── Coding System Metadata ──────────────────────────────────────────

export interface CodingSystemInfo {
  uri: CodingSystemUri;
  displayName: string;
  description: string;
}

/** Metadata for each coding system, keyed by constant name. */
export const CODING_SYSTEM_INFO: Record<
  keyof typeof CODING_SYSTEM,
  CodingSystemInfo
> = {
  ICD_11: {
    uri: CODING_SYSTEM.ICD_11,
    displayName: "ICD-11",
    description: "Diagnoses, mortality, morbidity coding",
  },
  ICD_10: {
    uri: CODING_SYSTEM.ICD_10,
    displayName: "ICD-10",
    description: "Legacy diagnosis coding (transitional)",
  },
  SNOMED_CT: {
    uri: CODING_SYSTEM.SNOMED_CT,
    displayName: "SNOMED CT",
    description:
      "Clinical findings, procedures, body structures, substances",
  },
  LOINC: {
    uri: CODING_SYSTEM.LOINC,
    displayName: "LOINC",
    description:
      "Laboratory tests, clinical observations, vital signs, document sections",
  },
  ATC: {
    uri: CODING_SYSTEM.ATC,
    displayName: "ATC",
    description: "Medication classification",
  },
  DICOM: {
    uri: CODING_SYSTEM.DICOM,
    displayName: "DICOM",
    description: "Medical imaging metadata, modality codes, SOP class UIDs",
  },
  UCUM: {
    uri: CODING_SYSTEM.UCUM,
    displayName: "UCUM",
    description: "Units of measure for observations and dosages",
  },
  CVX: {
    uri: CODING_SYSTEM.CVX,
    displayName: "CVX",
    description: "Vaccine product codes",
  },
  CPT: {
    uri: CODING_SYSTEM.CPT,
    displayName: "CPT",
    description: "Procedure coding for claims and billing",
  },
  RXNORM: {
    uri: CODING_SYSTEM.RXNORM,
    displayName: "RxNorm",
    description: "Drug terminology cross-mapping",
  },
  ISO_3166: {
    uri: CODING_SYSTEM.ISO_3166,
    displayName: "ISO 3166",
    description: "Country and subdivision codes",
  },
  HL7_ACT_CODE: {
    uri: CODING_SYSTEM.HL7_ACT_CODE,
    displayName: "HL7 ActCode",
    description: "Encounter type classification",
  },
  HL7_ROUTE: {
    uri: CODING_SYSTEM.HL7_ROUTE,
    displayName: "HL7 Route of Administration",
    description: "Medication administration routes",
  },
  HL7_OBSERVATION_CATEGORY: {
    uri: CODING_SYSTEM.HL7_OBSERVATION_CATEGORY,
    displayName: "HL7 Observation Category",
    description: "Observation category classification",
  },
  IMPILO_LOCAL: {
    uri: CODING_SYSTEM.IMPILO_LOCAL,
    displayName: "Impilo Local",
    description:
      "Zimbabwe-specific codes not covered by international standards",
  },
  IMPILO_FACILITY: {
    uri: CODING_SYSTEM.IMPILO_FACILITY,
    displayName: "Impilo Facility",
    description: "Facility identifier codes",
  },
  IMPILO_ITEM: {
    uri: CODING_SYSTEM.IMPILO_ITEM,
    displayName: "Impilo Item",
    description: "Inventory item codes",
  },
};

// ── FHIR R4 Coding Data Type ────────────────────────────────────────

/**
 * A reference to a code defined by a terminology system.
 * Aligned with FHIR R4 Coding data type.
 */
export interface Coding {
  /** The terminology system URI (e.g., "http://loinc.org"). */
  system: string;
  /** The version of the coding system (optional). */
  version?: string;
  /** The code value within the system. */
  code: string;
  /** Human-readable display name for the code. */
  display?: string;
  /** Whether the code was selected directly by the user. */
  userSelected?: boolean;
}

// ── FHIR R4 CodeableConcept Data Type ───────────────────────────────

/**
 * A concept that may be defined by one or more coding systems.
 * Aligned with FHIR R4 CodeableConcept data type.
 *
 * A single clinical concept (e.g., a diagnosis) can carry codings from
 * multiple systems simultaneously (e.g., ICD-11 + SNOMED CT).
 */
export interface CodeableConcept {
  /** One or more codings from different terminology systems. */
  coding: Coding[];
  /** Free-text fallback / clinician narrative. */
  text?: string;
}

// ── Clinical Data Classes ───────────────────────────────────────────

/**
 * Clinical data class and its primary required coding system.
 * See doctrine §3.2.
 */
export type ClinicalDataClass =
  | "DIAGNOSIS"
  | "LAB_TEST"
  | "OBSERVATION_VALUE"
  | "MEDICATION"
  | "PROCEDURE"
  | "BODY_SITE"
  | "SPECIMEN_TYPE"
  | "IMAGING_MODALITY"
  | "VACCINE"
  | "CLAIMS_PROCEDURE"
  | "VITAL_SIGN";

/** Maps each clinical data class to its required primary coding system. */
export const PRIMARY_CODING_SYSTEM: Record<
  ClinicalDataClass,
  CodingSystemUri
> = {
  DIAGNOSIS: CODING_SYSTEM.ICD_11,
  LAB_TEST: CODING_SYSTEM.LOINC,
  OBSERVATION_VALUE: CODING_SYSTEM.SNOMED_CT,
  MEDICATION: CODING_SYSTEM.ATC,
  PROCEDURE: CODING_SYSTEM.SNOMED_CT,
  BODY_SITE: CODING_SYSTEM.SNOMED_CT,
  SPECIMEN_TYPE: CODING_SYSTEM.SNOMED_CT,
  IMAGING_MODALITY: CODING_SYSTEM.DICOM,
  VACCINE: CODING_SYSTEM.CVX,
  CLAIMS_PROCEDURE: CODING_SYSTEM.CPT,
  VITAL_SIGN: CODING_SYSTEM.LOINC,
};

// ── Utility Functions ───────────────────────────────────────────────

/** All known coding system URIs. */
const KNOWN_SYSTEM_URIS = new Set<string>(Object.values(CODING_SYSTEM));

/** Check whether a URI corresponds to a known coding system. */
export function isKnownCodingSystem(uri: string): uri is CodingSystemUri {
  return KNOWN_SYSTEM_URIS.has(uri);
}

/**
 * Find a specific coding within a CodeableConcept by system URI.
 *
 * @param concept - The CodeableConcept to search
 * @param systemUri - The coding system URI to look for
 * @returns The first matching Coding, or undefined
 */
export function findCoding(
  concept: CodeableConcept | undefined | null,
  systemUri: string
): Coding | undefined {
  return concept?.coding?.find((c) => c.system === systemUri);
}

/**
 * Get the best display text from a CodeableConcept.
 * Prefers the first coding's display, falls back to text.
 */
export function getDisplayText(
  concept: CodeableConcept | undefined | null
): string | undefined {
  if (!concept) return undefined;
  const codingDisplay = concept.coding?.find((c) => c.display)?.display;
  return codingDisplay ?? concept.text;
}

/**
 * Create a single-coded CodeableConcept.
 *
 * @param systemUri - The coding system URI
 * @param code - The code value
 * @param display - The display name
 * @returns A new CodeableConcept
 */
export function createCodeableConcept(
  systemUri: string,
  code: string,
  display?: string
): CodeableConcept {
  return {
    coding: [{ system: systemUri, code, display }],
    text: display,
  };
}

/**
 * Structurally validate a Coding value.
 * Returns an array of issue strings (empty if valid).
 */
export function validateCoding(coding: Coding | undefined | null): string[] {
  const issues: string[] = [];

  if (!coding) {
    return ["Coding must not be null or undefined"];
  }

  if (!coding.system) {
    issues.push("Coding.system is required");
  } else if (!isKnownCodingSystem(coding.system)) {
    issues.push(`Unrecognized coding system URI: ${coding.system}`);
  }

  if (!coding.code) {
    issues.push("Coding.code is required");
  }

  return issues;
}

/**
 * Structurally validate a CodeableConcept.
 * Returns an array of issue strings (empty if valid).
 */
export function validateCodeableConcept(
  concept: CodeableConcept | undefined | null
): string[] {
  if (!concept) {
    return ["CodeableConcept must not be null or undefined"];
  }

  const hasCoding = concept.coding && concept.coding.length > 0;
  const hasText = concept.text && concept.text.trim().length > 0;

  if (!hasCoding && !hasText) {
    return ["CodeableConcept must have at least one coding or text"];
  }

  const issues: string[] = [];
  if (hasCoding) {
    concept.coding.forEach((c, i) => {
      validateCoding(c).forEach((issue) => {
        issues.push(`coding[${i}]: ${issue}`);
      });
    });
  }

  return issues;
}
