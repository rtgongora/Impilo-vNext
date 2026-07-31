package zw.gov.mohcc.impilo.medicine.thrombosis;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wells score for deep vein thrombosis.
 *
 * <p><strong>Source.</strong> Wells PS, Anderson DR, Rodger M, et al. <em>Evaluation of D-dimer in
 * the diagnosis of suspected deep-vein thrombosis.</em> N Engl J Med 2003;349:1227–1235. The primary
 * text is not vendored under {@code docs/reference}; the item weights are transcribed engineering
 * seeds pending MoHCC ratification.</p>
 *
 * <p><strong>The alternative-diagnosis item subtracts two points.</strong> It is the only negative
 * item in the score and it is the one most often left unanswered, because it asks the clinician to
 * commit to a judgement rather than record an observation. Leaving it out is not neutral: it inflates
 * the score by two and can move a patient from "unlikely" to "likely". This class therefore requires
 * an explicit answer rather than treating absence as a no.</p>
 *
 * <p><strong>What this class will not do.</strong> It sums the items and names the pre-test
 * probability. It does not order a D-dimer, does not interpret one, does not decide whether to
 * anticoagulate, and does not rule out DVT. The score's whole purpose is to set the prior against
 * which a subsequent test is read.</p>
 */
public final class WellsDvtScore {

    /** Bumped whenever an item weight or banding rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-wells-dvt-2003-1.0.0";

    private WellsDvtScore() {
    }

    /** The scored items. Each carries its published weight; one is negative. */
    public enum Criterion {

        /** Active cancer: treatment ongoing, within 6 months, or palliative. */
        ACTIVE_CANCER(1, "Active cancer (treatment ongoing, within 6 months, or palliative)"),

        /** Paralysis, paresis, or recent plaster immobilisation of the lower extremities. */
        PARALYSIS_PARESIS_OR_IMMOBILISATION(1,
                "Paralysis, paresis, or recent plaster immobilisation of the lower extremities"),

        /** Recently bedridden 3 days or more, or major surgery within 12 weeks requiring anaesthesia. */
        RECENTLY_BEDRIDDEN_OR_MAJOR_SURGERY(1,
                "Recently bedridden ≥3 days, or major surgery within 12 weeks requiring anaesthesia"),

        /** Localised tenderness along the distribution of the deep venous system. */
        LOCALISED_TENDERNESS(1, "Localised tenderness along the deep venous system"),

        /** Entire leg swollen. */
        ENTIRE_LEG_SWOLLEN(1, "Entire leg swollen"),

        /** Calf swelling more than 3 cm larger than the asymptomatic side. */
        CALF_SWELLING_OVER_3CM(1, "Calf swelling >3 cm larger than the asymptomatic side"),

        /** Pitting oedema confined to the symptomatic leg. */
        PITTING_OEDEMA_SYMPTOMATIC_LEG(1, "Pitting oedema confined to the symptomatic leg"),

        /** Collateral superficial veins, non-varicose. */
        COLLATERAL_SUPERFICIAL_VEINS(1, "Collateral superficial veins (non-varicose)"),

        /** Previously documented DVT. */
        PREVIOUSLY_DOCUMENTED_DVT(1, "Previously documented DVT"),

        /** An alternative diagnosis is at least as likely as DVT. The only negative item. */
        ALTERNATIVE_DIAGNOSIS_AS_LIKELY(-2,
                "An alternative diagnosis is at least as likely as DVT");

        private final int points;
        private final String label;

        Criterion(int points, String label) {
            this.points = points;
            this.label = label;
        }

        /** The item's published weight. Negative for {@link #ALTERNATIVE_DIAGNOSIS_AS_LIKELY}. */
        public int points() {
            return points;
        }

        /** The item wording as it should be presented to a clinician. */
        public String label() {
            return label;
        }
    }

    /** Pre-test probability bands from the two-level model. */
    public enum Probability {

        /** Score 2 or more: DVT likely. */
        LIKELY,

        /** Score 1 or less: DVT unlikely. */
        UNLIKELY
    }

    /**
     * Wells DVT from the set of criteria the clinician has confirmed present.
     *
     * @param present                     the criteria found present; an empty set is a valid answer
     *                                    meaning none were found, but null is refused because "none
     *                                    present" and "not assessed" are different claims
     * @param alternativeDiagnosisAssessed whether the clinician has actually considered the
     *                                    alternative-diagnosis item. Null or false is refused: that
     *                                    item subtracts two points, so skipping it inflates the score
     *                                    and can move a patient from unlikely to likely
     */
    public static Scoring of(Set<Criterion> present, Boolean alternativeDiagnosisAssessed) {
        if (present == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "No Wells criteria were recorded. An empty assessment is not the same as an"
                            + " assessment that found nothing, and the score is not calculated from"
                            + " one.");
        }
        if (!Boolean.TRUE.equals(alternativeDiagnosisAssessed)) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Whether an alternative diagnosis is at least as likely as DVT has not been"
                            + " assessed. That item subtracts two points, so leaving it unanswered"
                            + " inflates the score and can report a patient as 'DVT likely' who is not."
                            + " Record the judgement explicitly, either way.");
        }

        Set<Criterion> confirmed = present.isEmpty()
                ? EnumSet.noneOf(Criterion.class)
                : EnumSet.copyOf(present);

        int total = confirmed.stream().mapToInt(Criterion::points).sum();
        Probability probability = total >= 2 ? Probability.LIKELY : Probability.UNLIKELY;

        return new Scoring(total, probability, new LinkedHashSet<>(confirmed), null, null,
                CONTENT_VERSION);
    }

    private static Scoring refused(RefusalReason reason, String explanation) {
        return new Scoring(null, null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A Wells DVT score, or a stated reason there is none.
     *
     * @param score          the total, which may be negative; null when refused
     * @param probability    the two-level pre-test probability; null when refused
     * @param criteriaMet    the criteria that contributed, carried so a surface can show its working;
     *                       null when refused
     * @param reason         machine-readable refusal code; null when computed
     * @param explanation    clinician-readable refusal text; null when computed
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer score,
                          Probability probability,
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
