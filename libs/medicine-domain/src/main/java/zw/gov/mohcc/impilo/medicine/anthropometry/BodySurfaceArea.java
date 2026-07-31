package zw.gov.mohcc.impilo.medicine.anthropometry;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Body surface area by the DuBois and Mosteller formulae.
 *
 * <p><strong>Source.</strong> DuBois D, DuBois EF. <em>A formula to estimate the approximate surface
 * area if height and weight be known.</em> Arch Intern Med 1916;17:863–871. Mosteller RD.
 * <em>Simplified calculation of body-surface area.</em> N Engl J Med 1987;317:1098. Neither primary
 * text is vendored under {@code docs/reference}; the coefficients are transcribed engineering seeds
 * pending MoHCC ratification.</p>
 *
 * <p><strong>Both are returned, deliberately.</strong> They disagree — typically by one to three per
 * cent, and more at the extremes of body habitus. Returning one silently picks a convention on the
 * prescriber's behalf, and at chemotherapy doses a three per cent difference is a real difference in
 * milligrams. A surface that shows one must say which.</p>
 *
 * <p><strong>What this class will not do.</strong> It converts height and weight into an area. It
 * does not cap the area, does not apply the common 2.0 m² dose ceiling, and does not decide whether
 * to dose on actual, ideal or adjusted body weight. Those are prescribing policy and live in
 * governed CKP content.</p>
 */
public final class BodySurfaceArea {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published area. */
    public static final String CONTENT_VERSION = "impilo-bsa-dubois-mosteller-1.0.0";

    /** The unit every area in this class is expressed in. */
    public static final String UNIT = "m2";

    /**
     * Plausibility bounds on height in centimetres. These are transcription guards, not reference
     * ranges: a height of 17 is a metres/centimetres mix-up and a height of 1750 is a units slip.
     * Both would otherwise produce an area that looks like a real answer.
     */
    public static final double MIN_HEIGHT_CM = 100.0d;

    /** @see #MIN_HEIGHT_CM */
    public static final double MAX_HEIGHT_CM = 250.0d;

    /** Plausibility bounds on weight in kilograms. A weight of 0.7 is a grams/kilograms mix-up. */
    public static final double MIN_WEIGHT_KG = 20.0d;

    /** @see #MIN_WEIGHT_KG */
    public static final double MAX_WEIGHT_KG = 400.0d;

    private static final double DUBOIS_COEFFICIENT = 0.007184d;
    private static final double DUBOIS_HEIGHT_EXPONENT = 0.725d;
    private static final double DUBOIS_WEIGHT_EXPONENT = 0.425d;
    private static final double MOSTELLER_DIVISOR = 3600.0d;

    private BodySurfaceArea() {
    }

    /**
     * Body surface area from height and weight.
     *
     * @param heightCm standing height in centimetres; null is refused, not defaulted
     * @param weightKg body weight in kilograms; null is refused, not defaulted
     * @return a {@link Bsa} carrying both formulae's answers, or a named refusal reason
     */
    public static Bsa of(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null) {
            String missing = heightCm == null && weightKg == null ? "Height and weight are"
                    : heightCm == null ? "Height is" : "Weight is";
            return refused(RefusalReason.INPUT_MISSING,
                    missing + " not recorded, so body surface area cannot be calculated.");
        }

        double h = heightCm.doubleValue();
        double w = weightKg.doubleValue();

        if (h < MIN_HEIGHT_CM || h > MAX_HEIGHT_CM) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A height of " + heightCm.toPlainString() + " cm is outside the plausible range "
                            + MIN_HEIGHT_CM + "–" + MAX_HEIGHT_CM + " cm. This is usually a metres/centimetres"
                            + " mix-up; the value is not extrapolated.");
        }
        if (w < MIN_WEIGHT_KG || w > MAX_WEIGHT_KG) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A weight of " + weightKg.toPlainString() + " kg is outside the plausible range "
                            + MIN_WEIGHT_KG + "–" + MAX_WEIGHT_KG + " kg. This is usually a grams/kilograms"
                            + " mix-up; the value is not extrapolated.");
        }

        double duBois = DUBOIS_COEFFICIENT
                * Math.pow(h, DUBOIS_HEIGHT_EXPONENT)
                * Math.pow(w, DUBOIS_WEIGHT_EXPONENT);
        double mosteller = Math.sqrt(h * w / MOSTELLER_DIVISOR);

        if (!Double.isFinite(duBois) || !Double.isFinite(mosteller) || duBois <= 0 || mosteller <= 0) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "The formulae did not produce a finite area for the values given.");
        }

        return new Bsa(scale(duBois), scale(mosteller), null, null, CONTENT_VERSION);
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Bsa refused(RefusalReason reason, String explanation) {
        return new Bsa(null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A body surface area by each formula, or a stated reason there is none.
     *
     * @param duBoisM2      DuBois area in {@link #UNIT} to two decimals; null when refused
     * @param mostellerM2   Mosteller area in {@link #UNIT} to two decimals; null when refused
     * @param reason        machine-readable refusal code; null when computed
     * @param explanation   clinician-readable refusal text; null when computed
     * @param contentVersion the {@link #CONTENT_VERSION} that produced this result
     */
    public record Bsa(BigDecimal duBoisM2,
                      BigDecimal mostellerM2,
                      RefusalReason reason,
                      String explanation,
                      String contentVersion) {

        /** True when an area is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return duBoisM2 != null;
        }

        /**
         * The absolute difference between the two formulae, so a surface can show how far apart they
         * are rather than implying they agree. Null when refused.
         */
        public BigDecimal spreadM2() {
            if (!computed()) {
                return null;
            }
            return duBoisM2.subtract(mostellerM2).abs().round(new MathContext(3));
        }
    }
}
