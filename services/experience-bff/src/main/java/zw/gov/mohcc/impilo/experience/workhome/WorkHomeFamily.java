package zw.gov.mohcc.impilo.experience.workhome;

import java.util.Locale;
import java.util.Set;

/**
 * The Work Home family a resolved context's active {@code WorkMode} belongs to (Phase E4).
 * Branching between the different operational pictures a health worker sees lives in these
 * families' adapters, never in the page — a manager and a clinician at the same facility get
 * different sections because their active mode maps to a different family here, not because
 * the frontend decides what to hide.
 *
 * <p>Grouping mirrors {@code libs/tshepo-contracts/.../enums/WorkMode.java} (Phase B): the
 * two clinical-data-access-identical modes with different anchor breadth
 * ({@code CLINICAL_CARE}/{@code VIRTUAL_CARE}) and the two non-clinical operational-only mode
 * pairs ({@code TECHNICAL_SUPPORT}/{@code FACILITY_SUPPORT},
 * {@code REGULATORY_OPERATIONS}/{@code INSPECTION_COMPLIANCE}) share one family each because
 * they need the same section set, not because the underlying modes are interchangeable for
 * policy purposes — the PDP still enforces on the raw mode from the token, never on this
 * grouping.</p>
 */
public enum WorkHomeFamily {
    FACILITY_CLINICAL(Set.of("CLINICAL_CARE")),
    VIRTUAL_CARE(Set.of("VIRTUAL_CARE")),
    DEPARTMENT_MANAGEMENT(Set.of("DEPARTMENT_MANAGEMENT")),
    FACILITY_MANAGEMENT(Set.of("FACILITY_MANAGEMENT")),
    OVERSIGHT(Set.of("JURISDICTION_OPERATIONS")),
    PROGRAMME_MANAGEMENT(Set.of("PROGRAMME_MANAGEMENT")),
    SUPPORT(Set.of("TECHNICAL_SUPPORT", "FACILITY_SUPPORT")),
    REGULATORY(Set.of("REGULATORY_OPERATIONS", "INSPECTION_COMPLIANCE"));

    private final Set<String> modes;

    WorkHomeFamily(Set<String> modes) {
        this.modes = modes;
    }

    public boolean matches(String workMode) {
        return workMode != null && modes.contains(workMode.toUpperCase(Locale.ROOT));
    }

    /** Never defaults — an un-mapped or malformed mode resolves to no family, never a guess. */
    public static WorkHomeFamily forMode(String workMode) {
        if (workMode == null) return null;
        for (WorkHomeFamily family : values()) {
            if (family.matches(workMode)) return family;
        }
        return null;
    }
}
