package zw.gov.mohcc.impilo.medicine.common;

import java.util.Locale;

/**
 * The two-valued sex term the equations in this library are published against.
 *
 * <p>This is not a statement about gender identity and must not be used as one. CKD-EPI 2021 and
 * the WHO cardiovascular risk charts were each fitted on cohorts coded male/female, and their
 * coefficients only exist for those two values. A person's recorded gender belongs in the
 * demographic record; what an equation needs is the variable it was fitted on, and where that is
 * not known the equation does not run.</p>
 *
 * <p>{@link #parse(String)} returns null rather than guessing. A defaulted sex silently moves an
 * eGFR by roughly ten per cent — enough to cross a KDIGO category boundary — so an unparseable
 * value must reach the calculator as an absence and be refused there.</p>
 */
public enum Sex {

    FEMALE,
    MALE;

    /**
     * Maps the common recorded spellings onto the equation variable. Returns null for anything
     * unrecognised, empty or absent — deliberately, so the caller cannot receive a default.
     */
    public static Sex parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "F", "FEMALE", "WOMAN", "W" -> FEMALE;
            case "M", "MALE", "MAN" -> MALE;
            default -> null;
        };
    }
}
