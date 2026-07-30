package zw.gov.mohcc.impilo.medicine.hepatic;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

/**
 * The Child-Turcotte-Pugh classification of hepatic function.
 *
 * <p><strong>Source.</strong> Pugh RNH, Murray-Lyon IM, Dawson JL, Pietroni MC, Williams R.
 * <em>Transection of the oesophagus for bleeding oesophageal varices.</em> Br J Surg
 * 1973;60:646–649. The primary text is not vendored under {@code docs/reference}; the cut-points are
 * transcribed engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>Two of the five items are clinical judgements, not measurements.</strong> Ascites and
 * encephalopathy are graded by the assessing clinician, and this class takes them as explicit
 * enumerated answers. There is no default and no "assume none": a patient who has not been assessed
 * for encephalopathy is not a patient without encephalopathy, and scoring them as grade-free
 * produces a Child-Pugh A cirrhosis that may be a B or a C.</p>
 *
 * <p><strong>What this class will not do.</strong> It sums five items and names the class. It does
 * not estimate survival, does not decide operative risk, and does not select a transplant candidate.
 * The class is an input to those decisions, not a substitute for them.</p>
 */
public final class ChildPughScore {

    /** Bumped whenever a cut-point or banding rule changes a published class. */
    public static final String CONTENT_VERSION = "impilo-child-pugh-1973-1.0.0";

    /** The lowest attainable total: one point on each of the five items. */
    public static final int MIN_SCORE = 5;

    /** The highest attainable total: three points on each of the five items. */
    public static final int MAX_SCORE = 15;

    /** Bilirubin cut-points in µmol/L. */
    public static final BigDecimal BILIRUBIN_ONE_POINT_BELOW_UMOL_PER_L = BigDecimal.valueOf(34);

    /** @see #BILIRUBIN_ONE_POINT_BELOW_UMOL_PER_L */
    public static final BigDecimal BILIRUBIN_TWO_POINT_UPPER_UMOL_PER_L = BigDecimal.valueOf(50);

    /** Albumin cut-points in g/L. */
    public static final BigDecimal ALBUMIN_ONE_POINT_ABOVE_G_PER_L = BigDecimal.valueOf(35);

    /** @see #ALBUMIN_ONE_POINT_ABOVE_G_PER_L */
    public static final BigDecimal ALBUMIN_TWO_POINT_LOWER_G_PER_L = BigDecimal.valueOf(28);

    /** INR cut-points. */
    public static final BigDecimal INR_ONE_POINT_BELOW = BigDecimal.valueOf(1.7d);

    /** @see #INR_ONE_POINT_BELOW */
    public static final BigDecimal INR_TWO_POINT_UPPER = BigDecimal.valueOf(2.3d);

    private ChildPughScore() {
    }

    /** How much ascites the assessing clinician found. There is no "not assessed" value here — an
     *  unassessed patient is refused at the calculator rather than scored as {@link #NONE}. */
    public enum Ascites {

        /** No ascites on examination or imaging. */
        NONE(1),

        /** Ascites present and controlled by diuretics. */
        MILD_DIURETIC_RESPONSIVE(2),

        /** Ascites poorly controlled or refractory to diuretics. */
        MODERATE_OR_REFRACTORY(3);

        private final int points;

        Ascites(int points) {
            this.points = points;
        }

        /** The item's contribution to the total. */
        public int points() {
            return points;
        }
    }

    /** West Haven grade of hepatic encephalopathy, collapsed to the three Child-Pugh bands. */
    public enum Encephalopathy {

        /** No encephalopathy. */
        NONE(1),

        /** West Haven grade 1 or 2. */
        GRADE_1_OR_2(2),

        /** West Haven grade 3 or 4. */
        GRADE_3_OR_4(3);

        private final int points;

        Encephalopathy(int points) {
            this.points = points;
        }

        /** The item's contribution to the total. */
        public int points() {
            return points;
        }
    }

    /** The three published classes. */
    public enum PughClass {

        /** Total 5–6: well-compensated disease. */
        A("A", 5, 6),

        /** Total 7–9: significant functional compromise. */
        B("B", 7, 9),

        /** Total 10–15: decompensated disease. */
        C("C", 10, 15);

        private final String label;
        private final int lowerBoundInclusive;
        private final int upperBoundInclusive;

        PughClass(String label, int lowerBoundInclusive, int upperBoundInclusive) {
            this.label = label;
            this.lowerBoundInclusive = lowerBoundInclusive;
            this.upperBoundInclusive = upperBoundInclusive;
        }

        /** The single-letter label as it is written in the literature. */
        public String label() {
            return label;
        }

        /** Lowest total in this class. */
        public int lowerBoundInclusive() {
            return lowerBoundInclusive;
        }

        /** Highest total in this class. */
        public int upperBoundInclusive() {
            return upperBoundInclusive;
        }

        /** The class a total falls into, or null when the total is outside 5–15. */
        public static PughClass fromScore(Integer score) {
            if (score == null) {
                return null;
            }
            for (PughClass candidate : values()) {
                if (score >= candidate.lowerBoundInclusive && score <= candidate.upperBoundInclusive) {
                    return candidate;
                }
            }
            return null;
        }
    }

    /**
     * Child-Pugh from the five items.
     *
     * @param bilirubinMicromolPerLitre total bilirubin in µmol/L; null is refused
     * @param albuminGPerLitre          serum albumin in g/L; null is refused
     * @param inr                       international normalised ratio; null is refused
     * @param ascites                   the graded finding; null is refused, never assumed absent
     * @param encephalopathy            the graded finding; null is refused, never assumed absent
     */
    public static Scoring of(BigDecimal bilirubinMicromolPerLitre,
                             BigDecimal albuminGPerLitre,
                             BigDecimal inr,
                             Ascites ascites,
                             Encephalopathy encephalopathy) {
        if (bilirubinMicromolPerLitre == null) {
            return refused("Total bilirubin was not recorded");
        }
        if (albuminGPerLitre == null) {
            return refused("Serum albumin was not recorded");
        }
        if (inr == null) {
            return refused("INR was not recorded");
        }
        if (ascites == null) {
            return refused("Ascites was not assessed. A patient who has not been examined for ascites"
                    + " is not a patient without ascites, and scoring them as free of it would report a"
                    + " better class than the evidence supports");
        }
        if (encephalopathy == null) {
            return refused("Encephalopathy was not assessed. An unassessed patient is not an"
                    + " unaffected one, and assuming absence understates the class");
        }

        if (bilirubinMicromolPerLitre.signum() <= 0 || albuminGPerLitre.signum() <= 0
                || inr.signum() <= 0) {
            return new Scoring(null, null, null, null, null, null, null,
                    RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Bilirubin, albumin and INR must all be above zero; check the laboratory entries.",
                    CONTENT_VERSION);
        }

        int bilirubinPoints = bilirubinMicromolPerLitre.compareTo(BILIRUBIN_ONE_POINT_BELOW_UMOL_PER_L) < 0
                ? 1
                : bilirubinMicromolPerLitre.compareTo(BILIRUBIN_TWO_POINT_UPPER_UMOL_PER_L) <= 0 ? 2 : 3;

        int albuminPoints = albuminGPerLitre.compareTo(ALBUMIN_ONE_POINT_ABOVE_G_PER_L) > 0
                ? 1
                : albuminGPerLitre.compareTo(ALBUMIN_TWO_POINT_LOWER_G_PER_L) >= 0 ? 2 : 3;

        int inrPoints = inr.compareTo(INR_ONE_POINT_BELOW) < 0
                ? 1
                : inr.compareTo(INR_TWO_POINT_UPPER) <= 0 ? 2 : 3;

        int total = bilirubinPoints + albuminPoints + inrPoints
                + ascites.points() + encephalopathy.points();

        return new Scoring(total, PughClass.fromScore(total), bilirubinPoints, albuminPoints,
                inrPoints, ascites.points(), encephalopathy.points(), null, null, CONTENT_VERSION);
    }

    private static Scoring refused(String what) {
        return new Scoring(null, null, null, null, null, null, null,
                RefusalReason.INPUT_MISSING,
                what + ", so Child-Pugh cannot be calculated. All five items are required; a partial"
                        + " Child-Pugh is not a Child-Pugh.",
                CONTENT_VERSION);
    }

    /**
     * A Child-Pugh total and class, or a stated reason there is none.
     *
     * <p>The per-item points are carried so a surface can show which items drove the class rather
     * than presenting a bare total a clinician cannot check.</p>
     *
     * @param score               total 5–15; null when refused
     * @param pughClass           the class the total falls into; null when refused
     * @param bilirubinPoints     the bilirubin item's contribution; null when refused
     * @param albuminPoints       the albumin item's contribution; null when refused
     * @param inrPoints           the INR item's contribution; null when refused
     * @param ascitesPoints       the ascites item's contribution; null when refused
     * @param encephalopathyPoints the encephalopathy item's contribution; null when refused
     * @param reason              machine-readable refusal code; null when computed
     * @param explanation         clinician-readable refusal text; null when computed
     * @param contentVersion      the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer score,
                          PughClass pughClass,
                          Integer bilirubinPoints,
                          Integer albuminPoints,
                          Integer inrPoints,
                          Integer ascitesPoints,
                          Integer encephalopathyPoints,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return score != null;
        }
    }
}
