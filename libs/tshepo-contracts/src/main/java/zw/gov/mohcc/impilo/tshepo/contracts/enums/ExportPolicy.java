package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Export / bulk extract posture for the current authorization context.
 */
public enum ExportPolicy {
    PROHIBITED,
    AGGREGATE_ONLY,
    REDACTED,
    FULL_AUDITED;

    public static ExportPolicy fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL_AUDITED;
        }
        try {
            return ExportPolicy.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FULL_AUDITED;
        }
    }
}
