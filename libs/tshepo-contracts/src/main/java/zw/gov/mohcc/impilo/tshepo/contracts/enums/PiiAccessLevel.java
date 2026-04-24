package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * How strongly PII may be exposed, independent of clinical access.
 */
public enum PiiAccessLevel {
    NONE,
    MASKED,
    LIMITED,
    FULL;

    public static PiiAccessLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        try {
            return PiiAccessLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FULL;
        }
    }
}
