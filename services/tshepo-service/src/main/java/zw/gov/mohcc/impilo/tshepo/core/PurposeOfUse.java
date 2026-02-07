package zw.gov.mohcc.impilo.tshepo.core;

/**
 * Purpose-of-use codes as defined in the Impilo trust model.
 * Every API call must declare why the actor needs access.
 */
public enum PurposeOfUse {
    TREATMENT,
    PAYMENT,
    OPERATIONS,
    RESEARCH,
    PUBLIC_HEALTH,
    EMERGENCY,
    BREAK_GLASS,
    SYSTEM;

    public static PurposeOfUse fromHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
