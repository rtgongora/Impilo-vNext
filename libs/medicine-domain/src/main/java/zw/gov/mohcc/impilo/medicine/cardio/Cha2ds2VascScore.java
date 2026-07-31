package zw.gov.mohcc.impilo.medicine.cardio;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CHA₂DS₂-VASc stroke risk score in non-valvular atrial fibrillation.
 *
 * <p><strong>Source.</strong> Lip GYH, Nieuwlaat R, Pisters R, Lane DA, Crijns HJGM. <em>Refining
 * clinical risk stratification for predicting stroke and thromboembolism in atrial fibrillation using
 * a novel risk factor-based approach.</em> Chest 2010;137:263–272. The primary text is not vendored
 * under {@code docs/reference}; the item weights are transcribed engineering seeds pending MoHCC
 * ratification.</p>
 *
 * <p><strong>Age is scored in three bands, not by a single threshold.</strong> Under 65 scores
 * nothing, 65 to 74 scores one, and 75 or over scores two. Age is derived here from the recorded age
 * rather than accepted as a pre-computed band, so the banding cannot drift between callers.</p>
 *
 * <p><strong>The sex item is a risk modifier, not a risk factor.</strong> Female sex adds a point,
 * but the guidance is explicit that it does not by itself justify anticoagulation: a woman with a
 * score of 1 from sex alone carries the risk of a man with 0. This class therefore reports
 * {@link Scoring#scoreExcludingSex()} alongside the total, so a surface can present the distinction
 * the guidance requires instead of showing a bare 1 that reads as though it were an acquired risk.</p>
 *
 * <p><strong>What this class will not do.</strong> It counts risk factors. It does not weigh bleeding
 * risk, does not select an anticoagulant, does not decide whether to start one, and is not valid in
 * valvular AF or with a mechanical valve, where anticoagulation is indicated regardless of score.</p>
 */
public final class Cha2ds2VascScore {

    /** Bumped whenever an item weight or age band changes a published score. */
    public static final String CONTENT_VERSION = "impilo-cha2ds2-vasc-2010-1.0.0";

    /** At or above this age the age item scores two points. */
    public static final int AGE_TWO_POINT_THRESHOLD_YEARS = 75;

    /** At or above this age, and below {@link #AGE_TWO_POINT_THRESHOLD_YEARS}, the item scores one. */
    public static final int AGE_ONE_POINT_THRESHOLD_YEARS = 65;

    /** The highest attainable total: 9, when every item scores. */
    public static final int MAX_SCORE = 9;

    private Cha2ds2VascScore() {
    }

    /** The non-age, non-sex items. Age and sex are derived from the recorded demographics. */
    public enum Criterion {

        /** Congestive heart failure or left ventricular dysfunction. */
        CONGESTIVE_HEART_FAILURE(1, "Congestive heart failure or LV dysfunction"),

        /** Hypertension, or treated hypertension. */
        HYPERTENSION(1, "Hypertension (or on treatment)"),

        /** Diabetes mellitus. */
        DIABETES_MELLITUS(1, "Diabetes mellitus"),

        /** Prior stroke, TIA or thromboembolism. Weighted double. */
        PRIOR_STROKE_TIA_OR_THROMBOEMBOLISM(2, "Prior stroke, TIA or thromboembolism"),

        /** Vascular disease: prior MI, peripheral arterial disease or aortic plaque. */
        VASCULAR_DISEASE(1, "Vascular disease (prior MI, PAD or aortic plaque)");

        private final int points;
        private final String label;

        Criterion(int points, String label) {
            this.points = points;
            this.label = label;
        }

        /** The item's published weight. */
        public int points() {
            return points;
        }

        /** The item wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /**
     * CHA₂DS₂-VASc from demographics and the confirmed risk factors.
     *
     * @param ageYears age in years, from which the three-band age item is derived; null is refused
     * @param sex      the sex term the instrument uses; null is refused, because the sex item is
     *                 scored and cannot be assumed
     * @param present  the non-demographic criteria found present; an empty set is a valid answer
     *                 meaning none were found, but null is refused
     */
    public static Scoring of(Integer ageYears, Sex sex, Set<Criterion> present) {
        if (ageYears == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Age was not recorded. Age is scored in three bands here, so it cannot be omitted"
                            + " or approximated.");
        }
        if (sex == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Sex was not recorded. The sex item is scored, so it cannot be assumed.");
        }
        if (present == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "No risk factors were recorded. An unrecorded history is not the same as a history"
                            + " that found nothing, and the score is not calculated from one.");
        }
        if (ageYears < 0 || ageYears > 130) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "An age of " + ageYears + " years is not plausible.");
        }
        if (ageYears < 18) {
            return refused(RefusalReason.INSTRUMENT_NOT_APPLICABLE,
                    "CHA₂DS₂-VASc was derived and validated in adults with non-valvular atrial"
                            + " fibrillation. It is not applicable at " + ageYears + " years.");
        }

        Set<Criterion> confirmed = present.isEmpty()
                ? EnumSet.noneOf(Criterion.class)
                : EnumSet.copyOf(present);

        int agePoints = ageYears >= AGE_TWO_POINT_THRESHOLD_YEARS ? 2
                : ageYears >= AGE_ONE_POINT_THRESHOLD_YEARS ? 1 : 0;
        int sexPoints = sex == Sex.FEMALE ? 1 : 0;
        int criteriaPoints = confirmed.stream().mapToInt(Criterion::points).sum();

        int excludingSex = criteriaPoints + agePoints;
        int total = excludingSex + sexPoints;

        return new Scoring(total, excludingSex, agePoints, sexPoints,
                new LinkedHashSet<>(confirmed), null, null, CONTENT_VERSION);
    }

    private static Scoring refused(RefusalReason reason, String explanation) {
        return new Scoring(null, null, null, null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A CHA₂DS₂-VASc score, or a stated reason there is none.
     *
     * @param score            the total 0–{@link #MAX_SCORE}; null when refused
     * @param scoreExcludingSex the total without the sex point. Present so a surface can show that a
     *                          woman scoring 1 from sex alone carries a man's 0 risk, which is what
     *                          the guidance says and what a bare total hides; null when refused
     * @param agePoints        the age item's contribution, 0, 1 or 2; null when refused
     * @param sexPoints        the sex item's contribution, 0 or 1; null when refused
     * @param criteriaMet      the non-demographic criteria that contributed; null when refused
     * @param reason           machine-readable refusal code; null when computed
     * @param explanation      clinician-readable refusal text; null when computed
     * @param contentVersion   the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer score,
                          Integer scoreExcludingSex,
                          Integer agePoints,
                          Integer sexPoints,
                          Set<Criterion> criteriaMet,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return score != null;
        }
    }
}
