package zw.gov.mohcc.impilo.medicine.respiratory;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

/**
 * CURB-65 severity score for community-acquired pneumonia.
 *
 * <p><strong>Source.</strong> Lim WS, van der Eerden MM, Laing R, et al. <em>Defining community
 * acquired pneumonia severity on presentation to hospital: an international derivation and validation
 * study.</em> Thorax 2003;58:377–382. The primary text is not vendored under {@code docs/reference};
 * the cut-points are transcribed engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>Urea is the item most often missing at the point of decision.</strong> The score is
 * frequently used before the chemistry returns, and the common workaround — score the other four and
 * call it a CURB-65 — silently reports a maximum of 4 as though it were out of 5. This class refuses
 * instead, and exposes {@link #crb65(Boolean, BigDecimal, BigDecimal, BigDecimal, Integer)} as the
 * named, separately-banded instrument for when urea genuinely is not available. CRB-65 is a different
 * score with different bands, not a CURB-65 with a gap in it.</p>
 *
 * <p><strong>What this class will not do.</strong> It counts five items and names a risk band. It
 * does not decide admission, does not choose an antibiotic, and does not override a clinician who
 * judges a low-scoring patient unsafe for discharge. The published guidance itself subordinates the
 * score to clinical judgement and social circumstance.</p>
 */
public final class Curb65Score {

    /** Bumped whenever a cut-point or banding rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-curb65-2003-1.0.0";

    /** Urea above this scores a point. 7 mmol/L is the published threshold (≈ 19 mg/dL BUN). */
    public static final BigDecimal UREA_THRESHOLD_MMOL_PER_L = BigDecimal.valueOf(7);

    /** Respiratory rate at or above this scores a point. */
    public static final int RESPIRATORY_RATE_THRESHOLD_PER_MIN = 30;

    /** Systolic blood pressure below this scores a point. */
    public static final BigDecimal SYSTOLIC_THRESHOLD_MMHG = BigDecimal.valueOf(90);

    /** Diastolic blood pressure at or below this scores a point. */
    public static final BigDecimal DIASTOLIC_THRESHOLD_MMHG = BigDecimal.valueOf(60);

    /** Age at or above this scores a point — the "65" in the name. */
    public static final int AGE_THRESHOLD_YEARS = 65;

    private Curb65Score() {
    }

    /** Which instrument produced a given result. The two have different maxima and different bands. */
    public enum Instrument {

        /** All five items including urea. Range 0–5. */
        CURB_65("CURB-65", 5),

        /** The four items available without chemistry. Range 0–4, with its own bands. */
        CRB_65("CRB-65", 4);

        private final String label;
        private final int maximumScore;

        Instrument(String label, int maximumScore) {
            this.label = label;
            this.maximumScore = maximumScore;
        }

        /** The instrument name as it should be displayed. Never show a CRB-65 labelled CURB-65. */
        public String label() {
            return label;
        }

        /** The highest attainable score for this instrument. */
        public int maximumScore() {
            return maximumScore;
        }
    }

    /** The published risk bands. Band boundaries differ between the two instruments. */
    public enum RiskBand {

        /** Low mortality risk; outpatient management usually appropriate. */
        LOW,

        /** Intermediate risk; consider hospital-supervised treatment. */
        INTERMEDIATE,

        /** High risk; manage as severe pneumonia, assess for critical care. */
        HIGH
    }

    /**
     * Full CURB-65, all five items.
     *
     * @param confusion                new-onset confusion or an AMT of 8 or less; null is refused
     * @param ureaMmolPerLitre         serum urea in mmol/L; null is refused — use
     *                                 {@link #crb65(Boolean, BigDecimal, BigDecimal, BigDecimal, Integer)}
     *                                 instead when chemistry is genuinely unavailable
     * @param respiratoryRatePerMinute breaths per minute; null is refused
     * @param systolicMmHg             systolic pressure in mmHg; null is refused
     * @param diastolicMmHg            diastolic pressure in mmHg; null is refused
     * @param ageYears                 age in years; null is refused
     */
    public static Scoring of(Boolean confusion,
                             BigDecimal ureaMmolPerLitre,
                             Integer respiratoryRatePerMinute,
                             BigDecimal systolicMmHg,
                             BigDecimal diastolicMmHg,
                             Integer ageYears) {
        if (ureaMmolPerLitre == null) {
            return new Scoring(null, null, Instrument.CURB_65, null, null, null, null, null,
                    RefusalReason.INPUT_MISSING,
                    "Serum urea was not recorded, so CURB-65 cannot be calculated. Scoring the other"
                            + " four items and calling the result CURB-65 would report a maximum of 4"
                            + " as though it were out of 5. If chemistry is genuinely unavailable, use"
                            + " CRB-65, which is a distinct instrument with its own bands.",
                    CONTENT_VERSION);
        }
        return score(Instrument.CURB_65, confusion, ureaMmolPerLitre, respiratoryRatePerMinute,
                systolicMmHg, diastolicMmHg, ageYears);
    }

    /**
     * CRB-65, the four items that need no laboratory.
     *
     * <p>Named separately and banded separately on purpose. A result from this method must be
     * displayed as CRB-65, never as CURB-65.</p>
     */
    public static Scoring crb65(Boolean confusion,
                                Integer respiratoryRatePerMinute,
                                BigDecimal systolicMmHg,
                                BigDecimal diastolicMmHg,
                                Integer ageYears) {
        return score(Instrument.CRB_65, confusion, null, respiratoryRatePerMinute,
                systolicMmHg, diastolicMmHg, ageYears);
    }

    private static Scoring score(Instrument instrument,
                                 Boolean confusion,
                                 BigDecimal ureaMmolPerLitre,
                                 Integer respiratoryRatePerMinute,
                                 BigDecimal systolicMmHg,
                                 BigDecimal diastolicMmHg,
                                 Integer ageYears) {
        if (confusion == null) {
            return refused(instrument, "New-onset confusion was not assessed");
        }
        if (respiratoryRatePerMinute == null) {
            return refused(instrument, "Respiratory rate was not recorded");
        }
        if (systolicMmHg == null || diastolicMmHg == null) {
            return refused(instrument, "Blood pressure was not recorded");
        }
        if (ageYears == null) {
            return refused(instrument, "Age was not recorded");
        }

        if (ageYears < 0 || ageYears > 130) {
            return outOfRange(instrument, "An age of " + ageYears + " years is not plausible.");
        }
        if (respiratoryRatePerMinute < 0 || respiratoryRatePerMinute > 90) {
            return outOfRange(instrument, "A respiratory rate of " + respiratoryRatePerMinute
                    + " breaths per minute is outside the plausible range.");
        }
        if (systolicMmHg.signum() <= 0 || systolicMmHg.doubleValue() > 300
                || diastolicMmHg.signum() < 0 || diastolicMmHg.doubleValue() > 200) {
            return outOfRange(instrument, "The blood pressure entered is outside the plausible range.");
        }
        if (systolicMmHg.compareTo(diastolicMmHg) < 0) {
            return new Scoring(null, null, instrument, null, null, null, null, null,
                    RefusalReason.INPUTS_INCONSISTENT,
                    "The systolic pressure entered is below the diastolic, which usually means the two"
                            + " were transposed. The values are not reordered automatically.",
                    CONTENT_VERSION);
        }

        boolean confusionPoint = confusion;
        Boolean ureaPoint = instrument == Instrument.CURB_65
                ? ureaMmolPerLitre.compareTo(UREA_THRESHOLD_MMOL_PER_L) > 0
                : null;
        boolean respiratoryPoint = respiratoryRatePerMinute >= RESPIRATORY_RATE_THRESHOLD_PER_MIN;
        boolean pressurePoint = systolicMmHg.compareTo(SYSTOLIC_THRESHOLD_MMHG) < 0
                || diastolicMmHg.compareTo(DIASTOLIC_THRESHOLD_MMHG) <= 0;
        boolean agePoint = ageYears >= AGE_THRESHOLD_YEARS;

        int total = (confusionPoint ? 1 : 0)
                + (Boolean.TRUE.equals(ureaPoint) ? 1 : 0)
                + (respiratoryPoint ? 1 : 0)
                + (pressurePoint ? 1 : 0)
                + (agePoint ? 1 : 0);

        return new Scoring(total, band(instrument, total), instrument, confusionPoint, ureaPoint,
                respiratoryPoint, pressurePoint, agePoint, null, null, CONTENT_VERSION);
    }

    private static RiskBand band(Instrument instrument, int total) {
        if (instrument == Instrument.CURB_65) {
            return total <= 1 ? RiskBand.LOW : total == 2 ? RiskBand.INTERMEDIATE : RiskBand.HIGH;
        }
        return total == 0 ? RiskBand.LOW : total <= 2 ? RiskBand.INTERMEDIATE : RiskBand.HIGH;
    }

    private static Scoring refused(Instrument instrument, String what) {
        return new Scoring(null, null, instrument, null, null, null, null, null,
                RefusalReason.INPUT_MISSING,
                what + ", so " + instrument.label() + " cannot be calculated. Every item is required;"
                        + " a partial score understates severity in the patients it matters most for.",
                CONTENT_VERSION);
    }

    private static Scoring outOfRange(Instrument instrument, String what) {
        return new Scoring(null, null, instrument, null, null, null, null, null,
                RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE, what, CONTENT_VERSION);
    }

    /**
     * A CURB-65 or CRB-65 result, or a stated reason there is none.
     *
     * <p>The per-item flags are carried so a surface can show which items scored. {@code ureaPoint}
     * is null for CRB-65 — the item was not part of that instrument, which is different from having
     * scored zero.</p>
     *
     * @param score            the total; null when refused
     * @param band             the risk band for {@code instrument}; null when refused
     * @param instrument       which instrument this is — display this label, never the other one
     * @param confusionPoint   whether the confusion item scored; null when refused
     * @param ureaPoint        whether the urea item scored; null for CRB-65 and when refused
     * @param respiratoryPoint whether the respiratory rate item scored; null when refused
     * @param pressurePoint    whether the blood pressure item scored; null when refused
     * @param agePoint         whether the age item scored; null when refused
     * @param reason           machine-readable refusal code; null when computed
     * @param explanation      clinician-readable refusal text; null when computed
     * @param contentVersion   the {@link #CONTENT_VERSION} that produced this result
     */
    public record Scoring(Integer score,
                          RiskBand band,
                          Instrument instrument,
                          Boolean confusionPoint,
                          Boolean ureaPoint,
                          Boolean respiratoryPoint,
                          Boolean pressurePoint,
                          Boolean agePoint,
                          RefusalReason reason,
                          String explanation,
                          String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return score != null;
        }
    }
}
