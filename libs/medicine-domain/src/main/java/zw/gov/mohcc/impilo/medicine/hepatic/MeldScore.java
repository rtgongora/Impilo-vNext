package zw.gov.mohcc.impilo.medicine.hepatic;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Model for End-stage Liver Disease (MELD), the pre-2016 form without sodium.
 *
 * <p><strong>Source.</strong> Kamath PS, Wiesner RH, Malinchoc M, et al. <em>A model to predict
 * survival in patients with end-stage liver disease.</em> Hepatology 2001;33:464–470, with the UNOS
 * operational rules on flooring, the creatinine cap and dialysis. Primary texts are not vendored
 * under {@code docs/reference}; the coefficients are transcribed engineering seeds pending MoHCC
 * ratification.</p>
 *
 * <p><strong>The flooring rule is not a rounding convenience.</strong> Each of the three laboratory
 * values is raised to 1.0 before its logarithm is taken. Without that, a normal bilirubin of 0.4
 * mg/dL contributes a negative term and drags the score below its floor, producing a number that
 * says the liver is healthier than any liver can be. The floor is part of the published model.</p>
 *
 * <p><strong>Dialysis forces the creatinine to the cap.</strong> A patient dialysed twice or more in
 * the preceding week has a creatinine that reflects the dialysis schedule rather than renal function,
 * so the model substitutes the 4.0 mg/dL cap. Passing a post-dialysis creatinine without the flag
 * understates the score in exactly the patients who are sickest.</p>
 *
 * <p><strong>What this class will not do.</strong> It reports a number in the published 6–40 range.
 * It does not decide transplant priority, does not predict mortality for this individual, and is not
 * validated in acute liver failure or after transjugular shunting. Those are policy and clinical
 * judgement.</p>
 */
public final class MeldScore {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published score. */
    public static final String CONTENT_VERSION = "impilo-meld-2001-unos-1.0.0";

    /** Every laboratory value is raised to this floor before its logarithm is taken. */
    public static final BigDecimal LABORATORY_FLOOR = BigDecimal.ONE;

    /** Creatinine is capped here, and forced to it when the patient has been dialysed. */
    public static final BigDecimal CREATININE_CAP_MG_PER_DL = BigDecimal.valueOf(4.0d);

    /** The published score range. Values outside it are clamped, which is part of the model. */
    public static final int MIN_SCORE = 6;

    /** @see #MIN_SCORE */
    public static final int MAX_SCORE = 40;

    /** Bilirubin: 1 mg/dL = 17.1 µmol/L. Zimbabwe laboratories report µmol/L. */
    public static final BigDecimal MICROMOL_PER_LITRE_PER_MG_PER_DL_BILIRUBIN = BigDecimal.valueOf(17.1d);

    /** Creatinine: 1 mg/dL = 88.4 µmol/L. */
    public static final BigDecimal MICROMOL_PER_LITRE_PER_MG_PER_DL_CREATININE = BigDecimal.valueOf(88.4d);

    private static final double COEFFICIENT_BILIRUBIN = 3.78d;
    private static final double COEFFICIENT_INR = 11.2d;
    private static final double COEFFICIENT_CREATININE = 9.57d;
    private static final double INTERCEPT = 6.43d;

    private static final double MAX_BILIRUBIN_MG_PER_DL = 100.0d;
    private static final double MAX_CREATININE_MG_PER_DL = 25.0d;
    private static final double MIN_INR = 0.5d;
    private static final double MAX_INR = 20.0d;

    private MeldScore() {
    }

    /**
     * MELD from values in the units Zimbabwe laboratories report.
     *
     * @param bilirubinMicromolPerLitre  total bilirubin in µmol/L
     * @param creatinineMicromolPerLitre serum creatinine in µmol/L
     * @param inr                        international normalised ratio
     * @param dialysedTwiceInPastWeek    true forces the creatinine to {@link #CREATININE_CAP_MG_PER_DL};
     *                                   null is refused, because "not asked" and "no" are different
     *                                   claims and the difference changes the score
     */
    public static Meld fromSiUnits(BigDecimal bilirubinMicromolPerLitre,
                                   BigDecimal creatinineMicromolPerLitre,
                                   BigDecimal inr,
                                   Boolean dialysedTwiceInPastWeek) {
        BigDecimal bilirubinMgDl = convert(bilirubinMicromolPerLitre,
                MICROMOL_PER_LITRE_PER_MG_PER_DL_BILIRUBIN);
        BigDecimal creatinineMgDl = convert(creatinineMicromolPerLitre,
                MICROMOL_PER_LITRE_PER_MG_PER_DL_CREATININE);
        return of(bilirubinMgDl, creatinineMgDl, inr, dialysedTwiceInPastWeek);
    }

    private static BigDecimal convert(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.divide(factor, 6, RoundingMode.HALF_UP);
    }

    /**
     * MELD from values in mg/dL, the units the model is published in.
     *
     * <p>{@code MELD = 3.78·ln(bilirubin) + 11.2·ln(INR) + 9.57·ln(creatinine) + 6.43}, each
     * laboratory value floored at 1.0 and the creatinine additionally capped at 4.0.</p>
     */
    public static Meld of(BigDecimal bilirubinMgPerDl,
                          BigDecimal creatinineMgPerDl,
                          BigDecimal inr,
                          Boolean dialysedTwiceInPastWeek) {
        if (bilirubinMgPerDl == null || creatinineMgPerDl == null || inr == null) {
            String missing = bilirubinMgPerDl == null ? "Total bilirubin"
                    : creatinineMgPerDl == null ? "Serum creatinine" : "INR";
            return refused(RefusalReason.INPUT_MISSING,
                    missing + " was not recorded, so MELD cannot be calculated. All three laboratory"
                            + " terms are required; a partial MELD is not a MELD.");
        }
        if (dialysedTwiceInPastWeek == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Whether the patient has been dialysed twice or more in the past week was not"
                            + " recorded. Dialysis forces the creatinine term to its 4.0 mg/dL cap, so"
                            + " an unanswered question is not the same as a 'no' — assuming one would"
                            + " understate the score in the sickest patients.");
        }

        String fault = outOfRange("total bilirubin", bilirubinMgPerDl, 0.0d, MAX_BILIRUBIN_MG_PER_DL, "mg/dL");
        if (fault == null) {
            fault = outOfRange("serum creatinine", creatinineMgPerDl, 0.0d, MAX_CREATININE_MG_PER_DL, "mg/dL");
        }
        if (fault == null) {
            fault = outOfRange("INR", inr, MIN_INR, MAX_INR, "");
        }
        if (fault != null) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE, fault);
        }

        BigDecimal effectiveCreatinine = dialysedTwiceInPastWeek
                ? CREATININE_CAP_MG_PER_DL
                : creatinineMgPerDl.min(CREATININE_CAP_MG_PER_DL);

        double lnBilirubin = Math.log(floor(bilirubinMgPerDl).doubleValue());
        double lnInr = Math.log(floor(inr).doubleValue());
        double lnCreatinine = Math.log(floor(effectiveCreatinine).doubleValue());

        double raw = COEFFICIENT_BILIRUBIN * lnBilirubin
                + COEFFICIENT_INR * lnInr
                + COEFFICIENT_CREATININE * lnCreatinine
                + INTERCEPT;

        if (!Double.isFinite(raw)) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "The MELD equation did not produce a finite result for the values given.");
        }

        int rounded = (int) Math.round(raw);
        int clamped = Math.max(MIN_SCORE, Math.min(MAX_SCORE, rounded));

        return new Meld(clamped, clamped != rounded, effectiveCreatinine, dialysedTwiceInPastWeek,
                null, null, CONTENT_VERSION);
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.max(LABORATORY_FLOOR);
    }

    private static String outOfRange(String name, BigDecimal value, double min, double max, String unit) {
        double v = value.doubleValue();
        if (v <= min || v > max) {
            return "A " + name + " of " + value.toPlainString() + " " + unit + " is outside the plausible"
                    + " range. This is usually a unit mix-up or a misplaced decimal point; the value is"
                    + " not extrapolated.";
        }
        return null;
    }

    private static Meld refused(RefusalReason reason, String explanation) {
        return new Meld(null, false, null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * A MELD score, or a stated reason there is none.
     *
     * @param score                     the score, clamped to {@link #MIN_SCORE}–{@link #MAX_SCORE};
     *                                  null when refused
     * @param clampedToPublishedRange   true when the raw arithmetic fell outside 6–40 and was clamped,
     *                                  so a surface can say the score is at a boundary rather than
     *                                  implying it landed there naturally
     * @param creatinineUsedMgPerDl     the creatinine actually used after cap and dialysis substitution,
     *                                  carried so the substitution is auditable
     * @param dialysisSubstitutionApplied true when dialysis forced the creatinine to the cap
     * @param reason                    machine-readable refusal code; null when computed
     * @param explanation               clinician-readable refusal text; null when computed
     * @param contentVersion            the {@link #CONTENT_VERSION} that produced this result
     */
    public record Meld(Integer score,
                       boolean clampedToPublishedRange,
                       BigDecimal creatinineUsedMgPerDl,
                       Boolean dialysisSubstitutionApplied,
                       RefusalReason reason,
                       String explanation,
                       String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return score != null;
        }
    }
}
