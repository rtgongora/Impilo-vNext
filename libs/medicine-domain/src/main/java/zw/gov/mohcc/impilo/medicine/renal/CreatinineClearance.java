package zw.gov.mohcc.impilo.medicine.renal;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Creatinine clearance by the Cockcroft-Gault equation.
 *
 * <p><strong>Source.</strong> Cockcroft DW, Gault MH. <em>Prediction of creatinine clearance from
 * serum creatinine.</em> Nephron 1976;16:31–41. The primary text is not vendored under
 * {@code docs/reference}; the coefficients are transcribed engineering seeds pending MoHCC
 * ratification.</p>
 *
 * <p><strong>Why this exists alongside {@link EgfrCalculator}.</strong> They are not
 * interchangeable and choosing between them is a clinical decision. CKD-EPI reports an eGFR indexed
 * to 1.73 m² and is the right measure for staging chronic kidney disease. Cockcroft-Gault reports an
 * absolute clearance in mL/min and is the measure most renal drug-dosing tables were derived
 * against, so substituting an eGFR into a table written for creatinine clearance misdoses patients
 * at the extremes of body size. This class does not decide which a caller wants.</p>
 *
 * <p><strong>Standing caveats the caller must carry.</strong> The equation uses actual body weight,
 * so it overestimates clearance in obesity and underestimates it in oedema and ascites, where the
 * weight is fluid rather than tissue. It assumes a steady state, so it is meaningless in acute
 * kidney injury while the creatinine is still moving. It was derived almost entirely in men. None of
 * those adjustments are performed here, and no ideal or adjusted body weight is substituted — doing
 * so silently would replace the clinician's judgement with an assumption.</p>
 */
public final class CreatinineClearance {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published clearance. */
    public static final String CONTENT_VERSION = "impilo-crcl-cockcroft-gault-1.0.0";

    /** The unit every clearance in this class is expressed in. */
    public static final String UNIT = "mL/min";

    /** @see EgfrCalculator#MICROMOL_PER_LITRE_PER_MG_PER_DL */
    public static final BigDecimal MICROMOL_PER_LITRE_PER_MG_PER_DL = BigDecimal.valueOf(88.4d);

    /** The equation was derived in adults; below this the paediatric formulae apply. */
    public static final int MIN_AGE_YEARS = 18;

    /** Above this the recorded age is more likely a data-entry error than a patient. */
    public static final int MAX_AGE_YEARS = 120;

    /**
     * Plausibility bounds on actual body weight in kilograms. Transcription guards, not reference
     * ranges: a weight of 0.7 is a grams entry and would produce a clearance near zero, which reads
     * as established renal failure.
     */
    public static final double MIN_WEIGHT_KG = 20.0d;

    /** @see #MIN_WEIGHT_KG */
    public static final double MAX_WEIGHT_KG = 400.0d;

    /** @see EgfrCalculator#MIN_CREATININE_MG_PER_DL */
    public static final double MIN_CREATININE_MG_PER_DL = 0.10d;

    /** @see EgfrCalculator#MAX_CREATININE_MG_PER_DL */
    public static final double MAX_CREATININE_MG_PER_DL = 25.0d;

    private static final BigDecimal AGE_INTERCEPT = BigDecimal.valueOf(140);
    private static final BigDecimal DENOMINATOR_CONSTANT = BigDecimal.valueOf(72);
    private static final BigDecimal FEMALE_MULTIPLIER = BigDecimal.valueOf(0.85d);

    private CreatinineClearance() {
    }

    /**
     * Creatinine clearance from a creatinine reported in µmol/L, the unit Zimbabwe laboratories use.
     *
     * @param creatinineMicromolPerLitre serum creatinine in µmol/L; null is refused, not defaulted
     * @param ageYears                   age in completed years
     * @param weightKg                   actual body weight in kilograms
     * @param sex                        the equation variable, not recorded gender; null is refused
     */
    public static Clearance fromCreatinineMicromolPerLitre(BigDecimal creatinineMicromolPerLitre,
                                                           Integer ageYears,
                                                           BigDecimal weightKg,
                                                           Sex sex) {
        if (creatinineMicromolPerLitre == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum creatinine was not recorded, so creatinine clearance cannot be estimated.");
        }
        if (creatinineMicromolPerLitre.signum() <= 0) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Serum creatinine was recorded as " + creatinineMicromolPerLitre.toPlainString()
                            + " µmol/L. A creatinine of zero or below is not a possible result;"
                            + " check the laboratory entry.");
        }
        BigDecimal mgPerDl = creatinineMicromolPerLitre
                .divide(MICROMOL_PER_LITRE_PER_MG_PER_DL, 6, RoundingMode.HALF_UP);
        return fromCreatinineMgPerDl(mgPerDl, ageYears, weightKg, sex);
    }

    /**
     * Creatinine clearance from a creatinine reported in mg/dL, the unit the equation is published in.
     *
     * <p>The equation is {@code ((140 − age) × weight) / (72 × Scr)}, multiplied by 0.85 for
     * females.</p>
     *
     * @return a {@link Clearance} carrying either a value or a named refusal reason with a
     *         clinician-readable explanation. Never a default, never a sentinel.
     */
    public static Clearance fromCreatinineMgPerDl(BigDecimal creatinineMgPerDl,
                                                  Integer ageYears,
                                                  BigDecimal weightKg,
                                                  Sex sex) {
        if (creatinineMgPerDl == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum creatinine was not recorded, so creatinine clearance cannot be estimated.");
        }
        if (ageYears == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Age is not recorded. Cockcroft-Gault has an age term, so clearance cannot be"
                            + " estimated without it.");
        }
        if (weightKg == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Body weight is not recorded. Cockcroft-Gault is weight-based, and a defaulted"
                            + " weight would change the dose this clearance is used to select.");
        }
        if (sex == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Sex is not recorded. Cockcroft-Gault applies a 0.85 multiplier for females, so"
                            + " defaulting it moves the clearance by fifteen per cent.");
        }

        double scr = creatinineMgPerDl.doubleValue();
        if (scr < MIN_CREATININE_MG_PER_DL || scr > MAX_CREATININE_MG_PER_DL) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Serum creatinine of " + creatinineMgPerDl.toPlainString() + " mg/dL is outside the"
                            + " plausible range " + MIN_CREATININE_MG_PER_DL + "–" + MAX_CREATININE_MG_PER_DL
                            + " mg/dL. This is usually a unit mix-up between mg/dL and µmol/L.");
        }
        if (ageYears < MIN_AGE_YEARS) {
            return refused(RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                    "Cockcroft-Gault was derived in adults and is not valid at age " + ageYears
                            + ". Under " + MIN_AGE_YEARS + " years the paediatric formulae apply and none"
                            + " is implemented here.");
        }
        if (ageYears > MAX_AGE_YEARS) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A recorded age of " + ageYears + " years is outside the range a patient can"
                            + " occupy; check the date of birth.");
        }
        double w = weightKg.doubleValue();
        if (w < MIN_WEIGHT_KG || w > MAX_WEIGHT_KG) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A weight of " + weightKg.toPlainString() + " kg is outside the plausible range "
                            + MIN_WEIGHT_KG + "–" + MAX_WEIGHT_KG + " kg; check the entry.");
        }

        BigDecimal numerator = AGE_INTERCEPT.subtract(BigDecimal.valueOf(ageYears)).multiply(weightKg);
        if (numerator.signum() <= 0) {
            return refused(RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                    "At age " + ageYears + " the equation's (140 − age) term is zero or negative, so it"
                            + " produces no usable clearance. Cockcroft-Gault was never fitted this far"
                            + " out and the result is not extrapolated.");
        }

        BigDecimal denominator = DENOMINATOR_CONSTANT.multiply(creatinineMgPerDl);
        BigDecimal clearance = numerator.divide(denominator, 6, RoundingMode.HALF_UP);
        if (sex == Sex.FEMALE) {
            clearance = clearance.multiply(FEMALE_MULTIPLIER);
        }

        return new Clearance(clearance.setScale(1, RoundingMode.HALF_UP),
                creatinineMgPerDl.stripTrailingZeros(), weightKg, null, null, CONTENT_VERSION);
    }

    private static Clearance refused(RefusalReason reason, String explanation) {
        return new Clearance(null, null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A creatinine clearance, or a stated reason there is none.
     *
     * @param clearanceMlPerMin absolute clearance in {@link #UNIT}, to one decimal; null when refused
     * @param creatinineMgPerDl the creatinine actually used, so a µmol/L conversion is auditable
     * @param weightKg          the weight actually used, carried because it drives the result
     * @param reason            machine-readable refusal code; null when computed
     * @param explanation       clinician-readable refusal text; null when computed
     * @param contentVersion    the {@link #CONTENT_VERSION} that produced this result
     */
    public record Clearance(BigDecimal clearanceMlPerMin,
                            BigDecimal creatinineMgPerDl,
                            BigDecimal weightKg,
                            RefusalReason reason,
                            String explanation,
                            String contentVersion) {

        /** True when a clearance is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return clearanceMlPerMin != null;
        }
    }
}
