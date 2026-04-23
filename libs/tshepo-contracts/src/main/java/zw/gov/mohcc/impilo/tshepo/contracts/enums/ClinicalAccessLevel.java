package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Clinical depth allowed, independent of PII policy (both must be satisfied downstream).
 */
public enum ClinicalAccessLevel {
    NONE,
    SUMMARY,
    FULL;

    public static ClinicalAccessLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return ClinicalAccessLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
