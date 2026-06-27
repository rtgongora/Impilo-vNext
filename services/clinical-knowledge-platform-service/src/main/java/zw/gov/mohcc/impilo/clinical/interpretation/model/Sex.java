package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.util.Locale;

/**
 * Biological sex used to resolve a sex-specific reference interval. Never inferred — supplied by the
 * calling system from governed demographics. {@link #UNKNOWN} means "not supplied"; a sex-specific
 * interval is never selected for an UNKNOWN patient (fail-closed → widest applicable / none).
 */
public enum Sex {
    MALE,
    FEMALE,
    UNKNOWN;

    public static Sex fromString(String s) {
        if (s == null) {
            return UNKNOWN;
        }
        switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "MALE", "M": return MALE;
            case "FEMALE", "F": return FEMALE;
            default: return UNKNOWN;
        }
    }
}
