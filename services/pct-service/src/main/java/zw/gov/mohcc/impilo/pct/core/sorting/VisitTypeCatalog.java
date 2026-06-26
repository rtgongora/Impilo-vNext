package zw.gov.mohcc.impilo.pct.core.sorting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The visit-type catalog for the Sorting Desk (decision D5).
 *
 * <p>Pure, stateless. Visit-types are scoped by work-context (the six contexts in journey §5). The Sorting
 * Desk picks one visit-type per journey; the choice feeds the Cadre Engine ({@code visitType} input) and
 * routing. The catalog also derives a default fast-track flag and a fallback (emergency-assessment) when the
 * arrival reason cannot be classified — never block sorting on a missing reason.</p>
 */
public final class VisitTypeCatalog {

    /** A selectable visit-type within a context. */
    public record VisitType(String code, String label, boolean fastTrackDefault) {}

    private static final Map<String, List<VisitType>> BY_CONTEXT = new LinkedHashMap<>();

    static {
        BY_CONTEXT.put("OUTPATIENT", List.of(
                new VisitType("OPD_NEW", "New outpatient consultation", false),
                new VisitType("OPD_REVIEW", "Outpatient review / follow-up", false),
                new VisitType("CHRONIC_CARE", "Chronic disease care (HTN/DM/HIV/TB)", false),
                new VisitType("ANC", "Antenatal care", false),
                new VisitType("IMMUNISATION", "Immunisation", true),
                new VisitType("FAMILY_PLANNING", "Family planning", true),
                new VisitType("MINOR_AILMENT", "Minor ailment / single-service", true),
                new VisitType("PHARMACY_REFILL", "Medication refill only", true)));
        BY_CONTEXT.put("CASUALTY", List.of(
                new VisitType("ED_TRAUMA", "Emergency — trauma", true),
                new VisitType("ED_MEDICAL", "Emergency — medical", true),
                new VisitType("ED_RESUS", "Resuscitation", true)));
        BY_CONTEXT.put("INPATIENT", List.of(
                new VisitType("ADMISSION_ASSESSMENT", "Admission assessment", false),
                new VisitType("WARD_REVIEW", "Ward review", false)));
        BY_CONTEXT.put("PROCEDURE", List.of(
                new VisitType("DAY_SURGERY", "Day-surgery / day-case procedure", false),
                new VisitType("PRE_OP", "Pre-operative assessment", false),
                new VisitType("PROCEDURE_FOLLOWUP", "Post-procedure follow-up", false)));
        BY_CONTEXT.put("COMMUNITY", List.of(
                new VisitType("HOUSEHOLD_VISIT", "Household / outreach visit", false),
                new VisitType("COMMUNITY_SCREENING", "Community screening", true),
                new VisitType("DEFAULTER_TRACING", "Treatment defaulter tracing", false)));
        BY_CONTEXT.put("VIRTUAL", List.of(
                new VisitType("TELECONSULT_NEW", "New teleconsultation", false),
                new VisitType("TELECONSULT_REVIEW", "Teleconsultation review", false),
                new VisitType("TELE_SPECIALIST", "Specialist tele-referral", false)));
    }

    /** Fallback applied when a context has no catalog or the requested visit-type is unknown. */
    public static final VisitType EMERGENCY_ASSESSMENT =
            new VisitType("EMERGENCY_ASSESSMENT", "Emergency assessment (default)", true);

    private VisitTypeCatalog() {}

    /** Visit-types available for a context (never null; empty only for a genuinely unknown context). */
    public static List<VisitType> forContext(String context) {
        return BY_CONTEXT.getOrDefault(norm(context), List.of());
    }

    /** Every context's catalog, for the Sorting Desk UI. */
    public static Map<String, List<VisitType>> all() {
        return new LinkedHashMap<>(BY_CONTEXT);
    }

    /**
     * Resolve a requested visit-type within a context. Unknown codes fall back to EMERGENCY_ASSESSMENT
     * (D5 emergency behaviour: default to emergency assessment, never block).
     */
    public static VisitType resolve(String context, String requestedVisitType) {
        String want = norm(requestedVisitType);
        for (VisitType vt : forContext(context)) {
            if (vt.code().equals(want)) {
                return vt;
            }
        }
        return EMERGENCY_ASSESSMENT;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
