package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.Set;

/**
 * The closed vocabularies of the examination framework (brief.md §7), mirroring the CHECK
 * constraints in {@code V113__examination_framework.sql}.
 *
 * <p>As with {@link ProblemVocabulary} and {@link ProgrammeVocabulary}, these exist so an
 * unrecognised value is refused as a 400 naming the value and the allowed set rather than surfacing
 * as a Postgres constraint-violation 500, and {@code ExaminationVocabularyTest} parses the migration
 * to prove the Java and SQL copies have not drifted.</p>
 */
public final class ExaminationVocabulary {

    private ExaminationVocabulary() {}

    /** The twenty-two regions §7 names. */
    public static final Set<String> REGIONS = Set.of(
            "GENERAL_CONDITION", "VITAL_SIGNS", "ANTHROPOMETRY", "HYDRATION", "PALLOR", "JAUNDICE",
            "CYANOSIS", "OEDEMA", "LYMPH_NODES", "CARDIOVASCULAR", "RESPIRATORY", "ABDOMEN",
            "NEUROLOGY", "MUSCULOSKELETAL", "SKIN", "ENDOCRINE", "PERIPHERAL_VASCULAR", "FEET",
            "EYES", "FUNCTIONAL_STATUS", "COGNITION", "FRAILTY");

    public static final String NORMAL = "NORMAL";
    public static final String ABNORMAL = "ABNORMAL";
    public static final String NOT_EXAMINED = "NOT_EXAMINED";
    public static final String UNABLE_TO_EXAMINE = "UNABLE_TO_EXAMINE";
    public static final String DEFERRED = "DEFERRED";
    public static final String UNCERTAIN = "UNCERTAIN";

    /** The six states. */
    public static final Set<String> STATES =
            Set.of(NORMAL, ABNORMAL, NOT_EXAMINED, UNABLE_TO_EXAMINE, DEFERRED, UNCERTAIN);

    /**
     * The states that stand alone. Every other state must carry {@code detail} — what was found for
     * ABNORMAL and UNCERTAIN, why not for UNABLE_TO_EXAMINE and DEFERRED.
     *
     * <p>An abnormality with nothing written down reads to the next clinician as a normal
     * examination that somebody bothered to document, which is worse than a blank.</p>
     */
    public static final Set<String> STATES_NEEDING_NO_DETAIL = Set.of(NORMAL, NOT_EXAMINED);

    /**
     * The states that constitute a performed examination of that region.
     *
     * <p>UNCERTAIN counts: the examiner looked. NOT_EXAMINED, UNABLE_TO_EXAMINE and DEFERRED do not,
     * and grouping any of them with NORMAL is the single failure this framework exists to prevent.</p>
     */
    public static final Set<String> EXAMINED_STATES = Set.of(NORMAL, ABNORMAL, UNCERTAIN);

    /** The eleven diagrams §7 names. */
    public static final Set<String> GRAPHICS = Set.of(
            "CHEST_AUSCULTATION", "CARDIAC_FINDINGS", "ABDOMINAL_REGIONS", "NEUROLOGICAL_DEFICITS",
            "DERMATOMES", "PERIPHERAL_PULSES", "OEDEMA_SITES", "JOINTS", "SKIN_LESIONS",
            "DIABETIC_FOOT", "PRESSURE_INJURIES");

    public static final Set<String> LATERALITY = Set.of("LEFT", "RIGHT", "BILATERAL", "MIDLINE");

    /** Whether this state means the region was actually examined. */
    public static boolean wasExamined(String state) {
        return EXAMINED_STATES.contains(state);
    }

    /** Whether this state obliges the examiner to say what they found, or why they could not. */
    public static boolean requiresDetail(String state) {
        return !STATES_NEEDING_NO_DETAIL.contains(state);
    }
}
