package zw.gov.mohcc.impilo.medicine.metabolic;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serum sodium corrected for hyperglycaemia.
 *
 * <p><strong>Sources.</strong> Katz MA. <em>Hyperglycemia-induced hyponatremia — calculation of
 * expected serum sodium depression.</em> N Engl J Med 1973;289:843–844. Hillier TA, Abbott RD,
 * Barrett EJ. <em>Hyponatremia: evaluating the correction factor for hyperglycemia.</em> Am J Med
 * 1999;106:399–403. Neither primary text is vendored under {@code docs/reference}; both coefficients
 * are transcribed engineering seeds pending MoHCC ratification.</p>
 *
 * <p><strong>Both are returned, deliberately.</strong> Katz's 1.6 and Hillier's 2.4 disagree, and at
 * the glucose concentrations seen in diabetic ketoacidosis and hyperosmolar hyperglycaemic state the
 * gap between them is several millimoles. Hillier's factor fits the measured data better above about
 * 22 mmol/L glucose. Presenting one number hides a disagreement that changes how alarming a sodium
 * looks.</p>
 *
 * <p><strong>What the correction is for.</strong> Glucose is osmotically active and draws water into
 * the extracellular space, diluting sodium. The measured hyponatraemia is therefore partly an
 * artefact of the hyperglycaemia and partly real. Correcting says what the sodium would be once the
 * glucose is treated — it does not say the patient is not hyponatraemic, and it never justifies
 * treating sodium before glucose.</p>
 */
public final class CorrectedSodium {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published value. */
    public static final String CONTENT_VERSION = "impilo-corrected-sodium-katz-hillier-1.0.0";

    /** The unit corrected sodium is expressed in. */
    public static final String UNIT = "mmol/L";

    /**
     * The glucose above which a correction is meaningful, in mmol/L. At or below this the measured
     * sodium needs no correction and applying one produces a spurious adjustment in the wrong
     * direction.
     */
    public static final BigDecimal CORRECTION_THRESHOLD_GLUCOSE_MMOL_PER_L = BigDecimal.valueOf(5.5d);

    /** Katz's factor: millimoles of sodium per 5.5 mmol/L of glucose above the threshold. */
    public static final BigDecimal KATZ_FACTOR = BigDecimal.valueOf(1.6d);

    /** Hillier's factor, which fits the measured data better at high glucose. */
    public static final BigDecimal HILLIER_FACTOR = BigDecimal.valueOf(2.4d);

    /** Plausibility bounds on sodium in mmol/L — transcription guards, not reference ranges. */
    public static final double MIN_SODIUM_MMOL_PER_L = 90.0d;

    /** @see #MIN_SODIUM_MMOL_PER_L */
    public static final double MAX_SODIUM_MMOL_PER_L = 200.0d;

    /** Plausibility bounds on glucose in mmol/L. A glucose of 250 is a mg/dL entry. */
    public static final double MIN_GLUCOSE_MMOL_PER_L = 0.5d;

    /** @see #MIN_GLUCOSE_MMOL_PER_L */
    public static final double MAX_GLUCOSE_MMOL_PER_L = 100.0d;

    private CorrectedSodium() {
    }

    /**
     * Corrected sodium from a measured sodium and glucose, both in mmol/L — the units Zimbabwe
     * laboratories report.
     *
     * @param measuredSodiumMmolPerL measured serum sodium; null is refused, not defaulted
     * @param glucoseMmolPerL        concurrent serum glucose; null is refused, not defaulted
     */
    public static Correction of(BigDecimal measuredSodiumMmolPerL, BigDecimal glucoseMmolPerL) {
        if (measuredSodiumMmolPerL == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum sodium was not recorded, so it cannot be corrected.", null);
        }
        if (glucoseMmolPerL == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum glucose was not recorded. The correction is entirely a function of the"
                            + " glucose, so without one there is nothing to correct against.",
                    measuredSodiumMmolPerL);
        }

        double na = measuredSodiumMmolPerL.doubleValue();
        double glu = glucoseMmolPerL.doubleValue();

        if (na < MIN_SODIUM_MMOL_PER_L || na > MAX_SODIUM_MMOL_PER_L) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A sodium of " + measuredSodiumMmolPerL.toPlainString() + " mmol/L is outside the"
                            + " plausible range " + MIN_SODIUM_MMOL_PER_L + "–" + MAX_SODIUM_MMOL_PER_L
                            + " mmol/L; check the laboratory entry.",
                    measuredSodiumMmolPerL);
        }
        if (glu < MIN_GLUCOSE_MMOL_PER_L || glu > MAX_GLUCOSE_MMOL_PER_L) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A glucose of " + glucoseMmolPerL.toPlainString() + " mmol/L is outside the plausible"
                            + " range " + MIN_GLUCOSE_MMOL_PER_L + "–" + MAX_GLUCOSE_MMOL_PER_L
                            + " mmol/L. This is usually a mg/dL value entered in a mmol/L field.",
                    measuredSodiumMmolPerL);
        }
        if (glucoseMmolPerL.compareTo(CORRECTION_THRESHOLD_GLUCOSE_MMOL_PER_L) <= 0) {
            return refused(RefusalReason.INSTRUMENT_NOT_APPLICABLE,
                    "Glucose is " + glucoseMmolPerL.toPlainString() + " mmol/L, which is not elevated."
                            + " The correction describes sodium diluted by hyperglycaemia; with a normal"
                            + " glucose there is no dilution to reverse and the measured sodium stands as"
                            + " recorded. A low sodium here is a real hyponatraemia and needs its own"
                            + " explanation.",
                    measuredSodiumMmolPerL);
        }

        BigDecimal excess = glucoseMmolPerL.subtract(CORRECTION_THRESHOLD_GLUCOSE_MMOL_PER_L)
                .divide(CORRECTION_THRESHOLD_GLUCOSE_MMOL_PER_L, 6, RoundingMode.HALF_UP);

        BigDecimal katz = measuredSodiumMmolPerL.add(excess.multiply(KATZ_FACTOR))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal hillier = measuredSodiumMmolPerL.add(excess.multiply(HILLIER_FACTOR))
                .setScale(1, RoundingMode.HALF_UP);

        return new Correction(katz, hillier, measuredSodiumMmolPerL, glucoseMmolPerL,
                null, null, CONTENT_VERSION);
    }

    private static Correction refused(RefusalReason reason, String explanation, BigDecimal measured) {
        return new Correction(null, null, measured, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A corrected sodium by each published factor, or a stated reason there is none.
     *
     * @param katzMmolPerL           corrected sodium using Katz's 1.6; null when refused
     * @param hillierMmolPerL        corrected sodium using Hillier's 2.4; null when refused
     * @param measuredSodiumMmolPerL the measured value used, carried so the correction is auditable
     * @param glucoseMmolPerL        the glucose used; null when refused before it was read
     * @param reason                 machine-readable refusal code; null when computed
     * @param explanation            clinician-readable refusal text; null when computed
     * @param contentVersion         the {@link #CONTENT_VERSION} that produced this result
     */
    public record Correction(BigDecimal katzMmolPerL,
                             BigDecimal hillierMmolPerL,
                             BigDecimal measuredSodiumMmolPerL,
                             BigDecimal glucoseMmolPerL,
                             RefusalReason reason,
                             String explanation,
                             String contentVersion) {

        /** True when a corrected value is present. False means {@link #reason()} explains why not. */
        public boolean computed() {
            return katzMmolPerL != null;
        }
    }
}
