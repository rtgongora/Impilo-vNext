package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Classification of the resource being accessed (used by PDP and PEP alignment).
 */
public enum DataSensitivityClass {
    NON_SENSITIVE_OPERATIONAL,
    AGGREGATE_SENSITIVE,
    DEIDENTIFIED_PERSON_LEVEL,
    PSEUDONYMISED_PERSON_LEVEL,
    IDENTIFIED_OPERATIONAL,
    IDENTIFIED_CLINICAL_SUMMARY,
    FULL_CLINICAL,
    SPECIALLY_PROTECTED;

    public static DataSensitivityClass fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NON_SENSITIVE_OPERATIONAL;
        }
        try {
            return DataSensitivityClass.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NON_SENSITIVE_OPERATIONAL;
        }
    }
}
