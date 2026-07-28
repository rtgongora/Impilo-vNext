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
    public static final String HIV_PREVENTION = "HIV_PREVENTION"; // PrEP
    public static final String TB_PREVENTION = "TB_PREVENTION";   // TPT

    // Chronic-disease registers (brief.md §8). Lifelong care rather than a course with an end;
    // V112 makes them programmes rather than a parallel table, as V108's header anticipated.
    public static final String HYPERTENSION = "HYPERTENSION";
    public static final String DIABETES = "DIABETES";
    public static final String CHRONIC_KIDNEY_DISEASE = "CHRONIC_KIDNEY_DISEASE";
    public static final String CHRONIC_RESPIRATORY = "CHRONIC_RESPIRATORY"; // asthma and COPD

    /** The registers read for control rather than for a treatment outcome. */
    public static final Set<String> CHRONIC_REGISTERS =
            Set.of(HYPERTENSION, DIABETES, CHRONIC_KIDNEY_DISEASE, CHRONIC_RESPIRATORY);

    public static final Set<String> PROGRAMMES =
            Set.of(HIV_CARE, TB_TREATMENT, HIV_PREVENTION, TB_PREVENTION,
                    HYPERTENSION, DIABETES, CHRONIC_KIDNEY_DISEASE, CHRONIC_RESPIRATORY);

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
            "TREATMENT_FAILED",
            // The condition was not present. Without it, leaving a register because the diagnosis
            // was wrong had to be recorded as STOPPED_BY_PATIENT — making a clinician's error look
            // like a patient's refusal, in the patient's record.
            "DIAGNOSIS_REFUTED");

    /**
     * How a chronic condition is running against its agreed target. NULL — absent from this set —
     * means never assessed, which is deliberately not the same as NOT_CONTROLLED: a register is read
     * to decide who to recall, and the patient nobody has reviewed must not look like the patient
     * who is doing badly.
     */
    public static final Set<String> CONTROL_STATUSES = Set.of(
            "CONTROLLED",
            "PARTIALLY_CONTROLLED",
            "NOT_CONTROLLED",
            "TARGET_NOT_SET");

    // ── Treatment regimen ────────────────────────────────────────────────────────

    public static final Set<String> ART_STAGES = Set.of("FIRST_LINE", "SECOND_LINE", "THIRD_LINE");
    public static final Set<String> TB_STAGES = Set.of("INTENSIVE_PHASE", "CONTINUATION_PHASE");
    /** A prevention course (PrEP, TPT) has no ART line or TB phase — its stage is PREVENTIVE. */
    public static final Set<String> PREVENTION_STAGES = Set.of("PREVENTIVE");

    /** The union the {@code regimen_stage} column accepts. */
    public static final Set<String> REGIMEN_STAGES = union(union(ART_STAGES, TB_STAGES), PREVENTION_STAGES);

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
            TB_TREATMENT, TB_STAGES,
            HIV_PREVENTION, PREVENTION_STAGES,
            TB_PREVENTION, PREVENTION_STAGES);

    /**
     * Whether this programme is a chronic-disease register — lifelong care measured by control,
     * rather than a finite course measured by outcome. A register carries no treatment regimen
     * stage, so it is deliberately absent from {@link #STAGES_BY_PROGRAMME}: attaching an ART line
     * or a TB phase to a hypertension entry is a category error, and the absence makes
     * {@link #requireStageForProgramme} refuse it rather than needing a separate rule.
     */
    public static boolean isChronicRegister(String programme) {
        return CHRONIC_REGISTERS.contains(programme);
    }

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

    /**
     * Whether this programme is on the confidential lane (specially protected). The HIV programmes
     * are (care and PrEP — PrEP is HIV/SRH content about an HIV-negative person's risk); the TB
     * programmes are not (TB is notifiable, confidential from nobody).
     */
    public static boolean isConfidential(String programme) {
        return HIV_CARE.equals(programme) || HIV_PREVENTION.equals(programme);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.Set<String> u = new java.util.LinkedHashSet<>(a);
        u.addAll(b);
        return Set.copyOf(u);
    }
}
