package zw.gov.mohcc.impilo.medicine.metabolic;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Albumin-corrected serum calcium.
 *
 * <p><strong>Source.</strong> Payne RB, Little AJ, Williams RB, Milner JR. <em>Interpretation of
 * serum calcium in patients with abnormal serum proteins.</em> BMJ 1973;4:643–646. The primary text
 * is not vendored under {@code docs/reference}; the coefficient is a transcribed engineering seed
 * pending MoHCC ratification.</p>
 *
 * <p><strong>Why it matters.</strong> Roughly half of circulating calcium is albumin-bound, and only
 * the ionised fraction is physiologically active. A hypoalbuminaemic patient — common in chronic
 * illness, malnutrition and nephrotic syndrome — can show a total calcium in the reference range
 * while the corrected value is frankly high, or show a low total calcium that needs no treatment at
 * all. Acting on the uncorrected number is the error this exists to prevent.</p>
 *
 * <p><strong>Standing caveat the caller must carry.</strong> The correction is an approximation
 * fitted on a hospital population and performs poorly in renal failure, in acid-base disturbance and
 * in critical illness. Where the answer changes management, ionised calcium measured directly is the
 * better test. This class cannot tell that it is in one of those situations and does not try.</p>
 */
public final class CorrectedCalcium {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published value. */
    public static final String CONTENT_VERSION = "impilo-corrected-calcium-payne-1.0.0";

    /** The unit corrected calcium is expressed in. */
    public static final String UNIT = "mmol/L";

    /**
     * The albumin the correction normalises to, in g/L. Laboratories differ on this reference; 40
     * g/L is the value Payne's coefficient was derived against and changing it changes every result.
     */
    public static final BigDecimal REFERENCE_ALBUMIN_G_PER_L = BigDecimal.valueOf(40);

    /** Millimoles of calcium added per g/L of albumin below the reference. */
    public static final BigDecimal CORRECTION_PER_G_PER_L = BigDecimal.valueOf(0.02d);

    /**
     * Plausibility bounds on total calcium in mmol/L. Transcription guards, not reference ranges: a
     * calcium of 9.5 is a mg/dL value entered in a mmol/L field, and it would correct to a number
     * that looks survivable.
     */
    public static final double MIN_CALCIUM_MMOL_PER_L = 1.0d;

    /** @see #MIN_CALCIUM_MMOL_PER_L */
    public static final double MAX_CALCIUM_MMOL_PER_L = 5.0d;

    /** Plausibility bounds on albumin in g/L. A value of 4.2 is a g/dL entry in a g/L field. */
    public static final double MIN_ALBUMIN_G_PER_L = 5.0d;

    /** @see #MIN_ALBUMIN_G_PER_L */
    public static final double MAX_ALBUMIN_G_PER_L = 60.0d;

    private CorrectedCalcium() {
    }

    /**
     * Corrected calcium from a total calcium in mmol/L and an albumin in g/L, the units Zimbabwe
     * laboratories report.
     *
     * @param totalCalciumMmolPerL measured total serum calcium; null is refused, not defaulted
     * @param albuminGPerL         measured serum albumin; null is refused, not defaulted
     */
    public static Correction of(BigDecimal totalCalciumMmolPerL, BigDecimal albuminGPerL) {
        if (totalCalciumMmolPerL == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Total serum calcium was not recorded, so it cannot be corrected.", null);
        }
        if (albuminGPerL == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum albumin was not recorded. The correction exists precisely because albumin"
                            + " moves the total calcium, so it cannot be applied without one — and the"
                            + " uncorrected value must not be presented as if it had been corrected.",
                    totalCalciumMmolPerL);
        }

        double ca = totalCalciumMmolPerL.doubleValue();
        double alb = albuminGPerL.doubleValue();

        if (ca < MIN_CALCIUM_MMOL_PER_L || ca > MAX_CALCIUM_MMOL_PER_L) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A total calcium of " + totalCalciumMmolPerL.toPlainString() + " mmol/L is outside the"
                            + " plausible range " + MIN_CALCIUM_MMOL_PER_L + "–" + MAX_CALCIUM_MMOL_PER_L
                            + " mmol/L. This is usually a mg/dL value entered in a mmol/L field.",
                    totalCalciumMmolPerL);
        }
        if (alb < MIN_ALBUMIN_G_PER_L || alb > MAX_ALBUMIN_G_PER_L) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "An albumin of " + albuminGPerL.toPlainString() + " g/L is outside the plausible range "
                            + MIN_ALBUMIN_G_PER_L + "–" + MAX_ALBUMIN_G_PER_L + " g/L. This is usually a"
                            + " g/dL value entered in a g/L field.",
                    totalCalciumMmolPerL);
        }

        BigDecimal corrected = totalCalciumMmolPerL.add(
                        REFERENCE_ALBUMIN_G_PER_L.subtract(albuminGPerL).multiply(CORRECTION_PER_G_PER_L))
                .setScale(2, RoundingMode.HALF_UP);

        return new Correction(corrected, totalCalciumMmolPerL, albuminGPerL, null, null, CONTENT_VERSION);
    }

    private static Correction refused(RefusalReason reason, String explanation, BigDecimal measured) {
        return new Correction(null, measured, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A corrected calcium, or a stated reason there is none.
     *
     * @param correctedCalciumMmolPerL albumin-corrected calcium in {@link #UNIT}; null when refused
     * @param totalCalciumMmolPerL     the measured value used, carried so the correction is auditable
     * @param albuminGPerL             the albumin used; null when refused before it was read
     * @param reason                   machine-readable refusal code; null when computed
     * @param explanation              clinician-readable refusal text; null when computed
     * @param contentVersion           the {@link #CONTENT_VERSION} that produced this result
     */
    public record Correction(BigDecimal correctedCalciumMmolPerL,
                             BigDecimal totalCalciumMmolPerL,
                             BigDecimal albuminGPerL,
                             RefusalReason reason,
                             String explanation,
                             String contentVersion) {

        /** True when a corrected value is present. False means {@link #reason()} explains why not. */
        public boolean computed() {
            return correctedCalciumMmolPerL != null;
        }
    }
}
