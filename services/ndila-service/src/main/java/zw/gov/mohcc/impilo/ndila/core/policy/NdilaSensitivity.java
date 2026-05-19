package zw.gov.mohcc.impilo.ndila.core.policy;

/**
 * Ndila sensitivity classification used to drive provider selection,
 * payload minimization, and Tshepo authorization.
 */
public enum NdilaSensitivity {
    PUBLIC,
    INTERNAL,
    RESTRICTED,
    HIGHLY_RESTRICTED;

    public boolean atLeast(NdilaSensitivity other) {
        return this.ordinal() >= other.ordinal();
    }

    public static NdilaSensitivity parse(String s) {
        if (s == null || s.isBlank()) return PUBLIC;
        try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return PUBLIC; }
    }
}
