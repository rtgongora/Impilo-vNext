package zw.gov.mohcc.impilo.sharedkernel.terminology;

/**
 * Canonical coding systems adopted by the Health Operating System.
 *
 * <p>Each enum constant defines the system URI, human-readable name, and the
 * clinical domain it serves. These URIs are the single source of truth used in
 * FHIR resources, database records, event payloads, and API contracts.</p>
 *
 * <p>Aligns with the Healthcare Coding Standards doctrine
 * (see {@code docs/doctrine/healthcare-coding-standards.md}).</p>
 *
 * @see CodeableConcept
 * @see Coding
 */
public enum CodingSystem {

    // ── Primary International Standards ─────────────────────────────

    /** ICD-11 — International Classification of Diseases, 11th Revision (WHO). */
    ICD_11("http://id.who.int/icd/release/11", "ICD-11",
            "Diagnoses, mortality, morbidity coding"),

    /** ICD-10 — International Classification of Diseases, 10th Revision (WHO). */
    ICD_10("http://hl7.org/fhir/sid/icd-10", "ICD-10",
            "Legacy diagnosis coding (transitional)"),

    /** SNOMED CT — Systematized Nomenclature of Medicine, Clinical Terms. */
    SNOMED_CT("http://snomed.info/sct", "SNOMED CT",
            "Clinical findings, procedures, body structures, substances"),

    /** LOINC — Logical Observation Identifiers Names and Codes. */
    LOINC("http://loinc.org", "LOINC",
            "Laboratory tests, clinical observations, vital signs, document sections"),

    /** ATC — WHO Anatomical Therapeutic Chemical Classification. */
    ATC("http://www.whocc.no/atc", "ATC",
            "Medication classification"),

    /** DICOM — Digital Imaging and Communications in Medicine. */
    DICOM("http://dicom.nema.org/resources/ontology/DCM", "DICOM",
            "Medical imaging metadata, modality codes, SOP class UIDs"),

    // ── Supplementary Standards ─────────────────────────────────────

    /** UCUM — Unified Code for Units of Measure. */
    UCUM("http://unitsofmeasure.org", "UCUM",
            "Units of measure for observations and dosages"),

    /** CVX — CDC Vaccine Administered codes. */
    CVX("http://hl7.org/fhir/sid/cvx", "CVX",
            "Vaccine product codes"),

    /** CPT — Current Procedural Terminology (AMA). */
    CPT("http://www.ama-assn.org/go/cpt", "CPT",
            "Procedure coding for claims and billing"),

    /** RxNorm — Normalized drug names (NLM). */
    RXNORM("http://www.nlm.nih.gov/research/umls/rxnorm", "RxNorm",
            "Drug terminology cross-mapping"),

    /** ISO 3166 — Country and subdivision codes. */
    ISO_3166("urn:iso:std:iso:3166", "ISO 3166",
            "Country and subdivision codes"),

    // ── HL7 Vocabulary ──────────────────────────────────────────────

    /** HL7 v3 ActCode — encounter and act classification. */
    HL7_ACT_CODE("http://terminology.hl7.org/CodeSystem/v3-ActCode", "HL7 ActCode",
            "Encounter type classification"),

    /** HL7 Route of Administration — medication route codes. */
    HL7_ROUTE("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
            "HL7 Route of Administration",
            "Medication administration routes"),

    /** HL7 Observation Category. */
    HL7_OBSERVATION_CATEGORY("http://terminology.hl7.org/CodeSystem/observation-category",
            "HL7 Observation Category",
            "Observation category classification (vital-signs, laboratory, etc.)"),

    // ── Impilo Local Extensions ─────────────────────────────────────

    /** Impilo local coding namespace for jurisdiction-specific extensions. */
    IMPILO_LOCAL("http://impilo.gov.zw/coding", "Impilo Local",
            "Zimbabwe-specific codes not covered by international standards"),

    /** Impilo facility identifier system. */
    IMPILO_FACILITY("urn:zw:mohcc:facility", "Impilo Facility",
            "Facility identifier codes"),

    /** Impilo item identifier system. */
    IMPILO_ITEM("urn:zw:mohcc:item", "Impilo Item",
            "Inventory item codes");

    private final String uri;
    private final String displayName;
    private final String description;

    CodingSystem(String uri, String displayName, String description) {
        this.uri = uri;
        this.displayName = displayName;
        this.description = description;
    }

    /** Returns the canonical system URI used in FHIR and data exchange. */
    public String getUri() {
        return uri;
    }

    /** Returns the human-readable name of the coding system. */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns a brief description of the coding system's domain. */
    public String getDescription() {
        return description;
    }

    /**
     * Look up a CodingSystem by its URI.
     *
     * @param uri the system URI to look up
     * @return the matching CodingSystem
     * @throws IllegalArgumentException if no CodingSystem matches the URI
     */
    public static CodingSystem fromUri(String uri) {
        for (CodingSystem system : values()) {
            if (system.uri.equals(uri)) {
                return system;
            }
        }
        throw new IllegalArgumentException("Unknown coding system URI: " + uri);
    }

    /**
     * Check whether a URI corresponds to a known coding system.
     *
     * @param uri the system URI to check
     * @return {@code true} if the URI is recognized
     */
    public static boolean isKnown(String uri) {
        for (CodingSystem system : values()) {
            if (system.uri.equals(uri)) {
                return true;
            }
        }
        return false;
    }
}
