package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.Map;
import java.util.Set;

/**
 * The closed vocabularies of the HIV/TB programme enrolment and its treatment regimens, mirroring
 * the CHECK constraints in {@code V108__programme_enrolment.sql} and
 * {@code V109__treatment_regimen.sql}.
 *
 * <p>As with {@link ProblemVocabulary} and {@link MedicalEpisodeVocabulary}, these exist so that an
 * unrecognised value is refused as a 400 naming the value and the allowed set rather than surfacing
 * as a Postgres constraint-violation 500, and {@code ProgrammeVocabularyTest} parses the migrations
 * to prove the Java and SQL copies have not drifted.</p>
 */
public final class ProgrammeVocabulary {

    private ProgrammeVocabulary() {}

    // ── Enrolment ───────────────────────────────────────────────────────────────

    public static final String HIV_CARE = "HIV_CARE";
    public static final String TB_TREATMENT = "TB_TREATMENT";

    public static final Set<String> PROGRAMMES = Set.of(HIV_CARE, TB_TREATMENT);

    public static final Set<String> ENROLMENT_STATUSES = Set.of(
            "SCREENING",
            "ENROLLED",
            "ON_TREATMENT",
            "TREATMENT_INTERRUPTED",
            "EXITED");

    /** Statuses under which the person is still in the programme. */
    public static final Set<String> OPEN_ENROLMENT_STATUSES = Set.of(
            "SCREENING", "ENROLLED", "ON_TREATMENT", "TREATMENT_INTERRUPTED");

    /** The single closed status. Requires an exit reason and date. */
    public static final Set<String> CLOSED_ENROLMENT_STATUSES = Set.of("EXITED");

    public static final Set<String> EXIT_REASONS = Set.of(
            "TREATMENT_COMPLETED",
            "CURED",
            "TRANSFERRED_OUT",
            "LOST_TO_FOLLOW_UP",
            "DIED",
            "STOPPED_BY_PATIENT",
            "TREATMENT_FAILED");

    // ── Treatment regimen ────────────────────────────────────────────────────────

    public static final Set<String> ART_STAGES = Set.of("FIRST_LINE", "SECOND_LINE", "THIRD_LINE");
    public static final Set<String> TB_STAGES = Set.of("INTENSIVE_PHASE", "CONTINUATION_PHASE");

    /** The union the {@code regimen_stage} column accepts. */
    public static final Set<String> REGIMEN_STAGES = union(ART_STAGES, TB_STAGES);

    public static final Set<String> CHANGE_REASONS = Set.of(
            "NEW_START",
            "PHASE_TRANSITION",
            "TREATMENT_FAILURE",
            "TOXICITY",
            "DRUG_INTERACTION",
            "SIMPLIFICATION",
            "STOCK_OUT",
            "PATIENT_PREFERENCE",
            "COMPLETED");

    /**
     * Which regimen stages belong to which programme. A regimen stage is refused at the write if it
     * does not belong to the enrolment's programme — an ART line on a TB enrolment, or a TB phase on
     * HIV care, is a category error the union column cannot catch on its own.
     */
    public static final Map<String, Set<String>> STAGES_BY_PROGRAMME = Map.of(
            HIV_CARE, ART_STAGES,
            TB_TREATMENT, TB_STAGES);

    /**
     * Validates that the stage belongs to the programme, throwing with both named when it does not.
     * Assumes {@code stage} has already been normalised against {@link #REGIMEN_STAGES}.
     */
    public static void requireStageForProgramme(String programme, String stage) {
        Set<String> allowed = STAGES_BY_PROGRAMME.get(programme);
        if (allowed == null || !allowed.contains(stage)) {
            throw new IllegalArgumentException(
                    "Regimen stage '" + stage + "' does not belong to programme " + programme
                    + ". Allowed for this programme: " + allowed);
        }
    }

    /** Whether this programme is on the confidential lane (specially protected). HIV is; TB is not. */
    public static boolean isConfidential(String programme) {
        return HIV_CARE.equals(programme);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.Set<String> u = new java.util.LinkedHashSet<>(a);
        u.addAll(b);
        return Set.copyOf(u);
    }
}
