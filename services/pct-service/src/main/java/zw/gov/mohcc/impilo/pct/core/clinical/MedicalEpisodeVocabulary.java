package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.Set;

/**
 * The closed vocabularies of the medical episode, mirroring the CHECK constraints in
 * {@code V101__medical_episode.sql}.
 *
 * <p>As with {@link ProblemVocabulary}, these exist so an unrecognised value is refused as a 400
 * naming the value and the allowed set rather than surfacing as a constraint-violation 500, and
 * {@code MedicalEpisodeVocabularyTest} parses the migration to prove the two copies have not
 * drifted.</p>
 */
public final class MedicalEpisodeVocabulary {

    private MedicalEpisodeVocabulary() {}

    public static final Set<String> TYPES = Set.of(
            "ACUTE_ILLNESS",
            "CHRONIC_DISEASE",
            "MULTIMORBIDITY",
            "DIAGNOSTIC_WORKUP",
            "REHABILITATION",
            "PALLIATIVE",
            "PREVENTIVE");

    public static final Set<String> STATUSES = Set.of(
            "PLANNED",
            "ACTIVE",
            "ON_HOLD",
            "FINISHED",
            "CANCELLED");

    /**
     * Why an episode ended. Kept separate from the status because "completed", "the patient died",
     * "transferred" and "lost to follow-up" all leave the status FINISHED but are clinically and
     * statistically different endings — collapsing them makes an outcome indicator unbuildable.
     */
    public static final Set<String> END_REASONS = Set.of(
            "COMPLETED",
            "TRANSFERRED",
            "LOST_TO_FOLLOW_UP",
            "DIED",
            "PATIENT_DECLINED",
            "CANCELLED");

    public static final Set<String> PROBLEM_ROLES = Set.of("PRIMARY", "SECONDARY");

    /** Statuses under which the episode is still a live course of care. */
    public static final Set<String> OPEN_STATUSES = Set.of("PLANNED", "ACTIVE", "ON_HOLD");

    /** Statuses that require an end date, and forbid one when absent. */
    public static final Set<String> CLOSED_STATUSES = Set.of("FINISHED", "CANCELLED");
}
