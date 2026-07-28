package zw.gov.mohcc.impilo.medicine.renal;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estimated glomerular filtration rate by the CKD-EPI 2021 creatinine equation, race-free.
 *
 * <p><strong>Source.</strong> Inker LA, Eneanya ND, Coresh J, et al. <em>New Creatinine- and
 * Cystatin C-Based Equations to Estimate GFR without Race.</em> N Engl J Med 2021;385:1737–1749.
 * The primary text is <strong>not vendored</strong> under {@code docs/reference} — the coefficients
 * below are transcribed from the published equation and are engineering seeds pending MoHCC
 * ratification. Nothing here has been checked against a ministry-issued reference implementation.</p>
 *
 * <p><strong>Why the race-free equation.</strong> The 2009 CKD-EPI equation multiplied the result by
 * 1.159 for patients recorded as Black, which raised their eGFR and so delayed referral, transplant
 * listing and dose reduction. The 2021 refit removed the coefficient. There is no race term in this
 * class and none may be added: reintroducing it is a clinical harm, not a localisation.</p>
 *
 * <p><strong>What this class will not do.</strong> It reports a number and the KDIGO category that
 * number falls into. It does not decide whether to refer, whether to repeat the test, or whether a
 * medicine needs its dose changed. Those are policy and live in governed CKP content. It also never
 * returns a number it could not compute: a missing creatinine yields a stated refusal, because an
 * eGFR invented from an absence looks exactly like a normal kidney.</p>
 *
 * <p><strong>Standing caveats the caller must carry.</strong> The equation assumes a steady state.
 * In acute kidney injury the creatinine is still rising or falling and the eGFR it produces is
 * meaningless — this class cannot detect that and does not try. It is also unreliable at the
 * extremes of muscle mass (severe wasting, amputation, high muscle bulk) and in pregnancy, and it
 * is indexed to 1.73 m² body surface area, so absolute clearance for drug dosing needs
 * de-indexing by the patient's own surface area. None of those adjustments are performed here.</p>
 */
public final class EgfrCalculator {

    /** Bumped whenever a coefficient, bound or rounding rule changes a published eGFR. */
    public static final String CONTENT_VERSION = "impilo-egfr-ckd-epi-2021-1.0.0";

    /** The unit every eGFR in this class is expressed in. */
    public static final String UNIT = "mL/min/1.73m2";

    /**
     * Molar mass conversion for creatinine: 1 mg/dL = 88.4 µmol/L. Zimbabwe laboratories report
     * in µmol/L; the equation is published in mg/dL, so one of the two has to move and it must
     * move exactly once. A double conversion divides the creatinine by 88.4 twice and returns an
     * eGFR in the hundreds for a patient in established renal failure.
     */
    public static final BigDecimal MICROMOL_PER_LITRE_PER_MG_PER_DL = BigDecimal.valueOf(88.4d);

    /**
     * CKD-EPI 2021 was developed and validated in adults. Below this age the paediatric equations
     * apply — CKiD U25 or the bedside Schwartz formula — and neither is implemented here, so a
     * child is refused rather than scored on an adult curve.
     */
    public static final int MIN_AGE_YEARS = 18;

    /** Above this age the recorded age is more likely a data-entry error than a patient. */
    public static final int MAX_AGE_YEARS = 120;

    /**
     * Plausibility bounds on the creatinine itself, in mg/dL. Roughly 8.8 to 2210 µmol/L. These are
     * transcription guards, not reference ranges: a value below the floor is a units mix-up and a
     * value above the ceiling is a decimal-point slip. Both would otherwise produce an eGFR that
     * looks like a real answer.
     */
    public static final double MIN_CREATININE_MG_PER_DL = 0.10d;

    /** @see #MIN_CREATININE_MG_PER_DL */
    public static final double MAX_CREATININE_MG_PER_DL = 25.0d;

    private static final double INTERCEPT = 142.0d;
    private static final double KAPPA_FEMALE = 0.7d;
    private static final double KAPPA_MALE = 0.9d;
    private static final double ALPHA_FEMALE = -0.241d;
    private static final double ALPHA_MALE = -0.302d;
    private static final double HIGH_CREATININE_EXPONENT = -1.200d;
    private static final double AGE_BASE = 0.9938d;
    private static final double FEMALE_MULTIPLIER = 1.012d;

    private EgfrCalculator() {
    }

    /**
     * eGFR from a creatinine reported in µmol/L, the unit Zimbabwe laboratories use.
     *
     * <p>Source as for {@link #fromCreatinineMgPerDl}; the only difference is the unit conversion
     * at {@link #MICROMOL_PER_LITRE_PER_MG_PER_DL}, applied here and nowhere else.</p>
     *
     * @param creatinineMicromolPerLitre serum creatinine in µmol/L; null is refused, not defaulted
     * @param ageYears                   age in completed years at the time of the sample
     * @param sex                        the equation variable, not recorded gender; null is refused
     */
    public static Egfr fromCreatinineMicromolPerLitre(BigDecimal creatinineMicromolPerLitre,
                                                      Integer ageYears,
                                                      Sex sex) {
        if (creatinineMicromolPerLitre == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum creatinine was not recorded, so eGFR cannot be estimated.", null);
        }
        if (creatinineMicromolPerLitre.signum() <= 0) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Serum creatinine was recorded as " + creatinineMicromolPerLitre.toPlainString()
                            + " µmol/L. A creatinine of zero or below is not a possible result;"
                            + " check the laboratory entry.", null);
        }
        BigDecimal mgPerDl = creatinineMicromolPerLitre
                .divide(MICROMOL_PER_LITRE_PER_MG_PER_DL, 6, RoundingMode.HALF_UP);
        return fromCreatinineMgPerDl(mgPerDl, ageYears, sex);
    }

    /**
     * eGFR from a creatinine reported in mg/dL, the unit the equation is published in.
     *
     * <p>Source: Inker et al., N Engl J Med 2021;385:1737–1749 (CKD-EPI 2021 creatinine, race-free).
     * Primary text not vendored under {@code docs/reference}; the coefficients are transcribed
     * engineering seeds pending MoHCC ratification.</p>
     *
     * <p>The equation is
     * {@code 142 × min(Scr/κ, 1)^α × max(Scr/κ, 1)^−1.200 × 0.9938^age × 1.012 (if female)},
     * with κ = 0.7 and α = −0.241 for females, κ = 0.9 and α = −0.302 for males.</p>
     *
     * @return an {@link Egfr} carrying either a value and its KDIGO category, or a named refusal
     *         reason with a clinician-readable explanation. Never a default, never a sentinel.
     */
    public static Egfr fromCreatinineMgPerDl(BigDecimal creatinineMgPerDl, Integer ageYears, Sex sex) {
        if (creatinineMgPerDl == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Serum creatinine was not recorded, so eGFR cannot be estimated.", null);
        }
        if (ageYears == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Age is not recorded. CKD-EPI 2021 has an age term, so eGFR cannot be estimated"
                            + " without it.", creatinineMgPerDl);
        }
        if (sex == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "Sex is not recorded. CKD-EPI 2021 uses sex-specific coefficients, and defaulting"
                            + " one moves the result by around ten per cent — enough to cross a KDIGO"
                            + " category boundary.", creatinineMgPerDl);
        }

        double scr = creatinineMgPerDl.doubleValue();
        if (scr < MIN_CREATININE_MG_PER_DL || scr > MAX_CREATININE_MG_PER_DL) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "Serum creatinine of " + creatinineMgPerDl.toPlainString() + " mg/dL is outside the"
                            + " plausible range " + MIN_CREATININE_MG_PER_DL + "–" + MAX_CREATININE_MG_PER_DL
                            + " mg/dL. This is usually a unit mix-up between mg/dL and µmol/L or a"
                            + " misplaced decimal point; the value is not extrapolated.",
                    creatinineMgPerDl);
        }
        if (ageYears < MIN_AGE_YEARS) {
            return refused(RefusalReason.OUTSIDE_INSTRUMENT_RANGE,
                    "CKD-EPI 2021 was developed in adults and is not valid at age " + ageYears
                            + ". Under " + MIN_AGE_YEARS + " years the paediatric equations apply"
                            + " (CKiD U25 or bedside Schwartz), and neither is implemented here.",
                    creatinineMgPerDl);
        }
        if (ageYears > MAX_AGE_YEARS) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A recorded age of " + ageYears + " years is outside the range a patient can"
                            + " occupy; check the date of birth.", creatinineMgPerDl);
        }

        double kappa = sex == Sex.FEMALE ? KAPPA_FEMALE : KAPPA_MALE;
        double alpha = sex == Sex.FEMALE ? ALPHA_FEMALE : ALPHA_MALE;
        double ratio = scr / kappa;

        double raw = INTERCEPT
                * Math.pow(Math.min(ratio, 1.0d), alpha)
                * Math.pow(Math.max(ratio, 1.0d), HIGH_CREATININE_EXPONENT)
                * Math.pow(AGE_BASE, ageYears)
                * (sex == Sex.FEMALE ? FEMALE_MULTIPLIER : 1.0d);

        if (!Double.isFinite(raw) || raw <= 0.0d) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "The CKD-EPI 2021 equation did not produce a finite result for the values given.",
                    creatinineMgPerDl);
        }

        BigDecimal egfr = BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP);
        // The category is derived from the published value, not the unrounded one, so a stored
        // category always matches the number that was displayed beside it.
        return new Egfr(egfr, KdigoStage.fromEgfr(egfr), creatinineMgPerDl.stripTrailingZeros(),
                null, null, CONTENT_VERSION);
    }

    private static Egfr refused(RefusalReason reason, String explanation, BigDecimal creatinineMgPerDl) {
        return new Egfr(null, null, creatinineMgPerDl, reason, explanation, CONTENT_VERSION);
    }

    /**
     * An eGFR result, or a stated reason there is none.
     *
     * <p>Exactly one of {@code egfr} and {@code reason} is populated. {@link #computed()} is the
     * only safe way to branch on that: a caller that reads {@code egfr} without checking will get
     * null, which is the intended failure mode — a null pointer is a bug report, whereas a
     * defaulted 90 is a missed diagnosis.</p>
     *
     * @param egfr                 estimated GFR in {@link #UNIT}, to one decimal place; null when refused
     * @param kdigoStage           the KDIGO GFR category of {@code egfr}; null when refused
     * @param creatinineMgPerDl    the creatinine actually used, in the equation's own unit, so a
     *                             µmol/L conversion is auditable after the fact
     * @param reason               the machine-readable refusal code; null when computed
     * @param explanation          clinician-readable text for the refusal; null when computed
     * @param contentVersion       the {@link #CONTENT_VERSION} that produced this result
     */
    public record Egfr(BigDecimal egfr,
                       KdigoStage kdigoStage,
                       BigDecimal creatinineMgPerDl,
                       RefusalReason reason,
                       String explanation,
                       String contentVersion) {

        /** True when a value is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return egfr != null;
        }
    }
}
