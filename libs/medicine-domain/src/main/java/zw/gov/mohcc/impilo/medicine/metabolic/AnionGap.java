package zw.gov.mohcc.impilo.medicine.metabolic;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serum anion gap, its albumin correction, and the delta ratio.
 *
 * <p><strong>Source.</strong> Standard acid-base physiology; the albumin correction follows Figge J,
 * Jabor A, Kazda A, Fencl V. <em>Anion gap and hypoalbuminemia.</em> Crit Care Med
 * 1998;26:1807–1810. Primary texts are not vendored under {@code docs/reference}; the coefficient and
 * the reference gap are transcribed engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>Potassium is optional and the answer says which was used.</strong> Including it raises
 * the gap by roughly four, and a reference range quoted for one convention is wrong for the other.
 * The returned record names the convention rather than leaving the reader to infer it.</p>
 *
 * <p><strong>Why the albumin correction is not optional in practice.</strong> Albumin is the largest
 * unmeasured anion. A patient with an albumin of 20 g/L has a baseline gap around five lower than
 * the textbook range, so a "normal" gap of 12 in that patient is a raised gap hiding a metabolic
 * acidosis. Chronic illness makes hypoalbuminaemia common, which makes this the usual case, not the
 * exception.</p>
 *
 * <p><strong>What the delta ratio does and does not tell you.</strong> It compares the rise in the
 * gap with the fall in bicarbonate to suggest whether a second acid-base disturbance coexists. It is
 * a hypothesis generator computed from two noisy numbers and a population reference; it is not a
 * diagnosis, and this class attaches no interpretation to the value.</p>
 */
public final class AnionGap {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published value. */
    public static final String CONTENT_VERSION = "impilo-anion-gap-1.0.0";

    /** The unit every value in this class is expressed in. */
    public static final String UNIT = "mmol/L";

    /**
     * The reference anion gap the delta ratio is measured against, without potassium. Laboratories
     * differ; this is the widely-quoted midpoint and it is a seed, not a ratified national value.
     */
    public static final BigDecimal REFERENCE_GAP_MMOL_PER_L = BigDecimal.valueOf(12);

    /** The reference bicarbonate the delta ratio is measured against. */
    public static final BigDecimal REFERENCE_BICARBONATE_MMOL_PER_L = BigDecimal.valueOf(24);

    /** The albumin the correction normalises to, in g/L. */
    public static final BigDecimal REFERENCE_ALBUMIN_G_PER_L = BigDecimal.valueOf(40);

    /** Millimoles added to the gap per g/L of albumin below the reference. */
    public static final BigDecimal CORRECTION_PER_G_PER_L = BigDecimal.valueOf(0.25d);

    private static final double MIN_SODIUM = 90.0d;
    private static final double MAX_SODIUM = 200.0d;
    private static final double MIN_CHLORIDE = 50.0d;
    private static final double MAX_CHLORIDE = 160.0d;
    private static final double MIN_BICARBONATE = 1.0d;
    private static final double MAX_BICARBONATE = 60.0d;
    private static final double MIN_POTASSIUM = 1.0d;
    private static final double MAX_POTASSIUM = 10.0d;
    private static final double MIN_ALBUMIN = 5.0d;
    private static final double MAX_ALBUMIN = 60.0d;

    private AnionGap() {
    }

    /**
     * Anion gap from the standard electrolytes.
     *
     * @param sodiumMmolPerL      serum sodium; null is refused, not defaulted
     * @param chlorideMmolPerL    serum chloride; null is refused, not defaulted
     * @param bicarbonateMmolPerL serum bicarbonate; null is refused, not defaulted
     * @param potassiumMmolPerL   serum potassium; null means the potassium-free convention is used,
     *                            which is a legitimate choice rather than a missing input
     * @param albuminGPerL        serum albumin; null means no correction is computed and the record
     *                            says so rather than presenting the uncorrected gap as corrected
     */
    public static Gap of(BigDecimal sodiumMmolPerL,
                         BigDecimal chlorideMmolPerL,
                         BigDecimal bicarbonateMmolPerL,
                         BigDecimal potassiumMmolPerL,
                         BigDecimal albuminGPerL) {
        String missing = firstMissing(sodiumMmolPerL, chlorideMmolPerL, bicarbonateMmolPerL);
        if (missing != null) {
            return refused(RefusalReason.INPUT_MISSING,
                    missing + " was not recorded, so the anion gap cannot be calculated.");
        }

        String fault = outOfRange("sodium", sodiumMmolPerL, MIN_SODIUM, MAX_SODIUM);
        if (fault == null) {
            fault = outOfRange("chloride", chlorideMmolPerL, MIN_CHLORIDE, MAX_CHLORIDE);
        }
        if (fault == null) {
            fault = outOfRange("bicarbonate", bicarbonateMmolPerL, MIN_BICARBONATE, MAX_BICARBONATE);
        }
        if (fault == null && potassiumMmolPerL != null) {
            fault = outOfRange("potassium", potassiumMmolPerL, MIN_POTASSIUM, MAX_POTASSIUM);
        }
        if (fault == null && albuminGPerL != null) {
            fault = outOfRange("albumin", albuminGPerL, MIN_ALBUMIN, MAX_ALBUMIN);
        }
        if (fault != null) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE, fault);
        }

        boolean withPotassium = potassiumMmolPerL != null;
        BigDecimal cations = withPotassium ? sodiumMmolPerL.add(potassiumMmolPerL) : sodiumMmolPerL;
        BigDecimal gap = cations.subtract(chlorideMmolPerL).subtract(bicarbonateMmolPerL)
                .setScale(1, RoundingMode.HALF_UP);

        BigDecimal corrected = null;
        if (albuminGPerL != null) {
            corrected = gap.add(REFERENCE_ALBUMIN_G_PER_L.subtract(albuminGPerL)
                    .multiply(CORRECTION_PER_G_PER_L)).setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal deltaRatio = deltaRatio(corrected != null ? corrected : gap, bicarbonateMmolPerL);

        return new Gap(gap, corrected, deltaRatio, withPotassium, albuminGPerL != null,
                null, null, CONTENT_VERSION);
    }

    /**
     * The delta ratio: rise in gap over fall in bicarbonate. Null when the bicarbonate is at or above
     * the reference, where the denominator is zero or negative and the ratio has no meaning — a
     * patient without a metabolic acidosis has no delta to compare.
     */
    private static BigDecimal deltaRatio(BigDecimal gap, BigDecimal bicarbonate) {
        BigDecimal deltaGap = gap.subtract(REFERENCE_GAP_MMOL_PER_L);
        BigDecimal deltaBicarbonate = REFERENCE_BICARBONATE_MMOL_PER_L.subtract(bicarbonate);
        if (deltaBicarbonate.signum() <= 0 || deltaGap.signum() <= 0) {
            return null;
        }
        return deltaGap.divide(deltaBicarbonate, 2, RoundingMode.HALF_UP);
    }

    private static String firstMissing(BigDecimal sodium, BigDecimal chloride, BigDecimal bicarbonate) {
        if (sodium == null) {
            return "Serum sodium";
        }
        if (chloride == null) {
            return "Serum chloride";
        }
        if (bicarbonate == null) {
            return "Serum bicarbonate";
        }
        return null;
    }

    private static String outOfRange(String name, BigDecimal value, double min, double max) {
        double v = value.doubleValue();
        if (v < min || v > max) {
            return "A " + name + " of " + value.toPlainString() + " is outside the plausible range "
                    + min + "–" + max + ". This is usually a unit mix-up or a misplaced decimal point;"
                    + " the value is not extrapolated.";
        }
        return null;
    }

    private static Gap refused(RefusalReason reason, String explanation) {
        return new Gap(null, null, null, false, false, reason, explanation, CONTENT_VERSION);
    }

    /**
     * An anion gap, or a stated reason there is none.
     *
     * @param anionGapMmolPerL          the gap in {@link #UNIT}; null when refused
     * @param albuminCorrectedMmolPerL  the albumin-corrected gap; null when no albumin was supplied
     * @param deltaRatio                rise in gap over fall in bicarbonate; null when the
     *                                  bicarbonate is not below its reference, where it has no meaning
     * @param includesPotassium         true when the potassium-inclusive convention was used, so a
     *                                  reader is never left to guess which reference range applies
     * @param albuminCorrectionApplied  false means the gap is uncorrected and must not be read as
     *                                  corrected — in hypoalbuminaemia those differ materially
     * @param reason                    machine-readable refusal code; null when computed
     * @param explanation               clinician-readable refusal text; null when computed
     * @param contentVersion            the {@link #CONTENT_VERSION} that produced this result
     */
    public record Gap(BigDecimal anionGapMmolPerL,
                      BigDecimal albuminCorrectedMmolPerL,
                      BigDecimal deltaRatio,
                      boolean includesPotassium,
                      boolean albuminCorrectionApplied,
                      RefusalReason reason,
                      String explanation,
                      String contentVersion) {

        /** True when a gap is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return anionGapMmolPerL != null;
        }
    }
}
