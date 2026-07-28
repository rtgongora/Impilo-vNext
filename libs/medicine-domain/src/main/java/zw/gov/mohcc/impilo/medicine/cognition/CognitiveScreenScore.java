package zw.gov.mohcc.impilo.medicine.cognition;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Total-score arithmetic for a generic cognitive screening instrument.
 *
 * <p><strong>This class deliberately knows nothing about any particular instrument.</strong> It
 * takes a score and the instrument's maximum and reports the proportion of the maximum achieved,
 * banded into fifths. It does not know what MMSE, MoCA, IDEA or the Community Screening Instrument
 * for Dementia are, what their maxima are, or where their cut-offs sit.</p>
 *
 * <p><strong>Why the bands are proportions and not impairment categories.</strong> An instrument's
 * cut-off is not arithmetic. MMSE 24/30 and MoCA 26/30 are validated decisions about a population,
 * they move with education and language, and the MoCA carries an education adjustment that this
 * class does not apply. Encoding any of them here would put a clinical decision inside a library
 * that cannot be changed without a code release, and would be wrong for Zimbabwe in particular,
 * where the widely used screens are validated against local education profiles. The bands below are
 * arithmetic fifths of the maximum and mean nothing clinically. They exist so a surface can show a
 * consistent shape across instruments, and so a trend over time is legible.</p>
 *
 * <p>Where a real cut-off is needed, the caller supplies it — see
 * {@link Result#atOrBelowCutOff(Integer)}. The cut-off is then governed CKP content, versioned and
 * changeable by a ministry, which is where it belongs.</p>
 *
 * <p>Sources for the framing: the instruments named above are cited only to explain what this class
 * refuses to encode. No instrument's scoring rules, cut-offs or normative data are vendored under
 * {@code docs/reference} or reproduced here. Everything in this file is arithmetic and is an
 * engineering seed pending MoHCC ratification.</p>
 *
 * @param score            the total achieved; null when refused
 * @param maxScore         the instrument's maximum; null when refused
 * @param percentOfMaximum {@code score / maxScore} as a percentage to one decimal place; null when refused
 * @param band             the arithmetic proportion band; null when refused. Not an impairment category.
 * @param reason           machine-readable refusal code; null when computed
 * @param explanation      clinician-readable refusal text; null when computed
 * @param contentVersion   the {@link #CONTENT_VERSION} that produced this result
 */
public record CognitiveScreenScore(Integer score,
                                   Integer maxScore,
                                   BigDecimal percentOfMaximum,
                                   ProportionBand band,
                                   RefusalReason reason,
                                   String explanation,
                                   String contentVersion) {

    /** Bumped whenever a band boundary or bound changes. */
    public static final String CONTENT_VERSION = "impilo-cognitive-screen-proportion-1.0.0";

    /**
     * A maximum above this is a transcription error rather than an instrument. The longest screens
     * in common use top out well below it.
     */
    public static final int MAX_PLAUSIBLE_MAX_SCORE = 300;

    /**
     * Arithmetic fifths of an instrument's maximum.
     *
     * <p><strong>These are not impairment categories and must never be labelled as such.</strong>
     * A patient in {@link #BELOW_25_PERCENT} on one instrument and another in the same band on a
     * different instrument have nothing in common beyond the fraction. The band exists to make a
     * bare number legible and a trend visible, nothing more.</p>
     */
    public enum ProportionBand {

        /** 90% or more of the instrument's maximum. */
        AT_LEAST_90_PERCENT("90% or more of maximum"),

        /** 75% to under 90%. */
        FROM_75_TO_UNDER_90_PERCENT("75% to under 90% of maximum"),

        /** 50% to under 75%. */
        FROM_50_TO_UNDER_75_PERCENT("50% to under 75% of maximum"),

        /** 25% to under 50%. */
        FROM_25_TO_UNDER_50_PERCENT("25% to under 50% of maximum"),

        /** Below 25%. */
        BELOW_25_PERCENT("below 25% of maximum");

        private final String label;

        ProportionBand(String label) {
            this.label = label;
        }

        /** Wording that states the band as a proportion, so it cannot be read as a diagnosis. */
        public String label() {
            return label;
        }
    }

    /**
     * Band a total score against an instrument maximum.
     *
     * <p>Pure arithmetic; no instrument is identified and no cut-off is applied. A score above the
     * stated maximum is refused rather than clamped, because a clamped score is a real-looking
     * result derived from an impossible one, and the usual cause is the maximum for a different
     * instrument having been passed in — in which case every band produced from it is wrong.</p>
     *
     * @param score    total achieved; null is refused, never read as zero
     * @param maxScore the instrument's maximum; must be positive
     */
    public static CognitiveScreenScore of(Integer score, Integer maxScore) {
        if (score == null || maxScore == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    score == null
                            ? "No cognitive screen total was recorded. A missing score is not a score of"
                            + " zero, which would be the worst possible result."
                            : "The instrument's maximum score was not supplied, so the total cannot be"
                            + " expressed as a proportion.");
        }
        if (maxScore <= 0) {
            return refused(RefusalReason.INPUTS_INCONSISTENT,
                    "The instrument's maximum score was given as " + maxScore + ". A maximum of zero or"
                            + " below leaves nothing to score against.");
        }
        if (maxScore > MAX_PLAUSIBLE_MAX_SCORE) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "The instrument's maximum score was given as " + maxScore + ", above the plausible"
                            + " ceiling of " + MAX_PLAUSIBLE_MAX_SCORE + ". Check which field was entered.");
        }
        if (score < 0) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A cognitive screen total of " + score + " is below zero and cannot be scored.");
        }
        if (score > maxScore) {
            return refused(RefusalReason.INPUTS_INCONSISTENT,
                    "A total of " + score + " exceeds the instrument's maximum of " + maxScore + "."
                            + " This is usually the maximum for a different instrument; the score is"
                            + " refused rather than capped, because capping would produce a full-marks"
                            + " result from an impossible one.");
        }

        BigDecimal percent = BigDecimal.valueOf(score)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxScore), 1, RoundingMode.HALF_UP);

        return new CognitiveScreenScore(score, maxScore, percent, bandFor(percent), null, null,
                CONTENT_VERSION);
    }

    private static ProportionBand bandFor(BigDecimal percent) {
        double value = percent.doubleValue();
        if (value >= 90.0d) {
            return ProportionBand.AT_LEAST_90_PERCENT;
        }
        if (value >= 75.0d) {
            return ProportionBand.FROM_75_TO_UNDER_90_PERCENT;
        }
        if (value >= 50.0d) {
            return ProportionBand.FROM_50_TO_UNDER_75_PERCENT;
        }
        if (value >= 25.0d) {
            return ProportionBand.FROM_25_TO_UNDER_50_PERCENT;
        }
        return ProportionBand.BELOW_25_PERCENT;
    }

    private static CognitiveScreenScore refused(RefusalReason reason, String explanation) {
        return new CognitiveScreenScore(null, null, null, null, reason, explanation, CONTENT_VERSION);
    }

    /** True when a band is present. False means {@link #reason()} explains why it is not. */
    public boolean computed() {
        return band != null;
    }

    /**
     * Whether the total is at or below a cut-off <em>the caller supplies</em>.
     *
     * <p>The comparison is arithmetic; the cut-off is not. Passing one in makes the caller — in
     * practice governed CKP content carrying the instrument, its version and its education
     * adjustment — the owner of the clinical decision, which is the point. This class will never
     * hold a default cut-off for anything.</p>
     *
     * @return null when there is no score or no cut-off, so an absent comparison cannot be read as
     *         a negative one
     */
    public Boolean atOrBelowCutOff(Integer cutOff) {
        if (!computed() || cutOff == null) {
            return null;
        }
        return score <= cutOff;
    }

    /**
     * The difference between this total and an earlier one on the same instrument.
     *
     * <p>Returns null when either total is missing or the two were scored against different maxima —
     * a six-point drop between a 30-point and a 20-point instrument is not a decline, it is a
     * category error. Whether any given change is clinically meaningful is policy.</p>
     */
    public Integer changeFrom(CognitiveScreenScore earlier) {
        if (!computed() || earlier == null || !earlier.computed()
                || !maxScore.equals(earlier.maxScore())) {
            return null;
        }
        return score - earlier.score();
    }
}
