package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The closed vocabularies of the problem list.
 *
 * <p>These sets are the same sets the database CHECK constraints enforce
 * ({@code V100__problem_model_certainty_and_status.sql}). They exist here as well so that an
 * unrecognised value is refused as a 400 naming the value and the allowed set, rather than reaching
 * Postgres and coming back as a constraint-violation 500 that tells a clinician nothing.</p>
 *
 * <p>Two copies of one vocabulary is exactly the kind of duplication that drifts, so
 * {@code ProblemVocabularyTest} parses the migration and asserts the sets are identical. Adding a
 * value in one place and not the other fails the build.</p>
 */
public final class ProblemVocabulary {

    private ProblemVocabulary() {}

    /**
     * What kind of clinical statement the problem is. Orthogonal to {@link #CERTAINTIES}: a symptom
     * can be confirmed while a diagnosis is only suspected.
     */
    public static final Set<String> CATEGORIES = Set.of(
            "DIAGNOSIS",
            "SYMPTOM",
            "SYNDROME",
            "GUIDELINE_CLASSIFICATION",
            "RISK_FACTOR",
            "FUNCTIONAL_CONSEQUENCE",
            "SOCIAL_CIRCUMSTANCE");

    /** How certain the recorder was. Absent is a legitimate answer and is represented by null. */
    public static final Set<String> CERTAINTIES = Set.of(
            "SUSPECTED",
            "DIFFERENTIAL",
            "WORKING",
            "CONFIRMED",
            "REFUTED");

    public static final Set<String> CLINICAL_STATUSES = Set.of(
            "ACTIVE",
            "RECURRENCE",
            "RELAPSE",
            "REMISSION",
            "INACTIVE",
            "RESOLVED");

    /**
     * Statuses under which a problem is still something the clinician must act on. Excludes
     * REMISSION deliberately — a condition in remission is still the patient's, and still needs
     * monitoring, so it belongs on the list even though it is not currently active.
     */
    public static final Set<String> OPEN_STATUSES = Set.of(
            "ACTIVE", "RECURRENCE", "RELAPSE", "REMISSION");

    /** Statuses that mean the problem has come back rather than started. */
    public static final Set<String> RECURRENT_STATUSES = Set.of("RECURRENCE", "RELAPSE");

    /**
     * Normalises and validates, or throws with the value and the allowed set named.
     *
     * @param value the raw caller value; null or blank returns {@code null} when {@code required}
     *              is false, because "not stated" is a real answer for certainty and severity
     */
    public static String require(String field, Object value, Set<String> allowed, boolean required) {
        String s = value == null ? null : String.valueOf(value).trim();
        if (s == null || s.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("Missing field: " + field);
            }
            return null;
        }
        String normalised = s.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalised)) {
            throw new IllegalArgumentException(
                    "Unrecognised " + field + ": '" + s + "'. Allowed: " + sorted(allowed));
        }
        return normalised;
    }

    private static Set<String> sorted(Set<String> values) {
        return new LinkedHashSet<>(values.stream().sorted().toList());
    }
}
