package zw.gov.mohcc.impilo.sharedkernel.terminology;

/**
 * Clinical data classes and their required coding systems.
 *
 * <p>Defines the expected primary coding system for each category of clinical
 * data, as specified in the Healthcare Coding Standards doctrine §3.2.</p>
 *
 * <p>Used by validation logic to enforce that the correct coding system is
 * applied to each data type.</p>
 */
public enum ClinicalDataClass {

    /** Diagnosis codes — primary system: ICD-11. */
    DIAGNOSIS(CodingSystem.ICD_11, "Diagnosis"),

    /** Laboratory test / observation type — primary system: LOINC. */
    LAB_TEST(CodingSystem.LOINC, "Laboratory test"),

    /** Coded observation value — primary system: SNOMED CT. */
    OBSERVATION_VALUE(CodingSystem.SNOMED_CT, "Observation value"),

    /** Medication — primary system: ATC. */
    MEDICATION(CodingSystem.ATC, "Medication"),

    /** Clinical procedure — primary system: SNOMED CT. */
    PROCEDURE(CodingSystem.SNOMED_CT, "Procedure"),

    /** Body site — primary system: SNOMED CT. */
    BODY_SITE(CodingSystem.SNOMED_CT, "Body site"),

    /** Specimen type — primary system: SNOMED CT. */
    SPECIMEN_TYPE(CodingSystem.SNOMED_CT, "Specimen type"),

    /** Imaging modality — primary system: DICOM. */
    IMAGING_MODALITY(CodingSystem.DICOM, "Imaging modality"),

    /** Vaccine product — primary system: CVX. */
    VACCINE(CodingSystem.CVX, "Vaccine"),

    /** Billing/claims procedure — primary system: CPT. */
    CLAIMS_PROCEDURE(CodingSystem.CPT, "Claims procedure"),

    /** Vital sign observation type — primary system: LOINC. */
    VITAL_SIGN(CodingSystem.LOINC, "Vital sign");

    private final CodingSystem primarySystem;
    private final String displayName;

    ClinicalDataClass(CodingSystem primarySystem, String displayName) {
        this.primarySystem = primarySystem;
        this.displayName = displayName;
    }

    /** Returns the primary (required) coding system for this data class. */
    public CodingSystem getPrimarySystem() {
        return primarySystem;
    }

    /** Returns the human-readable name of this data class. */
    public String getDisplayName() {
        return displayName;
    }
}
