package zw.gov.mohcc.impilo.paediatrics.growth;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Classifies acute malnutrition from mid-upper-arm circumference, bilateral pitting
 * oedema and weight-for-age, and explains the conclusion in clinical language.
 *
 * <p><strong>Engineering seed — requires MoHCC ratification.</strong> The cut-offs below
 * follow WHO guidance for children 6–59 months, but the executable thresholds for
 * Zimbabwe must be confirmed against the national IMAM protocol before this drives
 * treatment decisions in production. The thresholds are constants here precisely so a
 * governed content release can replace them in one reviewable place.</p>
 *
 * <p>Two safety rules are structural rather than configurable:</p>
 * <ul>
 *   <li>Bilateral pitting oedema is severe acute malnutrition regardless of any
 *       measurement — a child with nutritional oedema can have a deceptively normal weight.</li>
 *   <li>An assessment with no usable input returns {@link NutritionStatus#NOT_ASSESSED},
 *       never "adequate". A blank field is not a normal finding.</li>
 * </ul>
 *
 * <p>Wasting is normally defined by weight-for-length/height. That indicator is not yet
 * available (see {@link GrowthIndicator}), so this classifier reports MUAC- and
 * oedema-based conclusions and says so, rather than silently substituting weight-for-age,
 * which detects underweight — a different thing from wasting.</p>
 */
public final class NutritionClassifier {

    /** MUAC below this is severe acute malnutrition in children 6–59 months. */
    public static final BigDecimal MUAC_SEVERE_CM = BigDecimal.valueOf(11.5d);

    /** MUAC below this (and at or above the severe cut-off) is moderate acute malnutrition. */
    public static final BigDecimal MUAC_MODERATE_CM = BigDecimal.valueOf(12.5d);

    /** MUAC below this is a watch signal even when not yet moderate. */
    public static final BigDecimal MUAC_AT_RISK_CM = BigDecimal.valueOf(13.5d);

    /** MUAC is only interpretable in this age window. */
    public static final int MUAC_MIN_AGE_DAYS = 183;
    public static final int MUAC_MAX_AGE_DAYS = 1824;

    private static final BigDecimal WAZ_SEVERE = BigDecimal.valueOf(-3);
    private static final BigDecimal WAZ_MODERATE = BigDecimal.valueOf(-2);

    public static final String CONTENT_VERSION = "engineering-seed-1.0.0";

    private NutritionClassifier() {
    }

    /**
     * @param ageDays     chronological age; MUAC is only interpreted between 6 and 59 months
     * @param muacCm      mid-upper-arm circumference in centimetres, null when not measured
     * @param oedema      bilateral pitting oedema finding
     * @param weightForAgeZ weight-for-age z-score, null when not scorable
     */
    public static NutritionAssessment classify(Integer ageDays, BigDecimal muacCm, Oedema oedema,
                                               BigDecimal weightForAgeZ) {
        List<String> findings = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        NutritionStatus status = NutritionStatus.NOT_ASSESSED;
        boolean anyInput = false;

        if (oedema == Oedema.PRESENT) {
            anyInput = true;
            status = NutritionStatus.SEVERE_ACUTE_MALNUTRITION;
            findings.add("Bilateral pitting oedema is present, which is severe acute malnutrition"
                    + " irrespective of weight or arm circumference.");
        } else if (oedema == Oedema.ABSENT) {
            anyInput = true;
        } else {
            gaps.add("Bilateral pitting oedema was not assessed.");
        }

        if (muacCm != null && muacCm.compareTo(BigDecimal.ZERO) > 0) {
            if (muacInterpretable(ageDays)) {
                anyInput = true;
                if (muacCm.compareTo(MUAC_SEVERE_CM) < 0) {
                    status = status.mostSevere(NutritionStatus.SEVERE_ACUTE_MALNUTRITION);
                    findings.add("MUAC " + muacCm + " cm is below the " + MUAC_SEVERE_CM
                            + " cm severe acute malnutrition cut-off.");
                } else if (muacCm.compareTo(MUAC_MODERATE_CM) < 0) {
                    status = status.mostSevere(NutritionStatus.MODERATE_ACUTE_MALNUTRITION);
                    findings.add("MUAC " + muacCm + " cm is between " + MUAC_SEVERE_CM + " and "
                            + MUAC_MODERATE_CM + " cm: moderate acute malnutrition.");
                } else if (muacCm.compareTo(MUAC_AT_RISK_CM) < 0) {
                    status = status.mostSevere(NutritionStatus.AT_RISK);
                    findings.add("MUAC " + muacCm + " cm is above the moderate cut-off but low enough"
                            + " to warrant follow-up.");
                } else {
                    status = status.mostSevere(NutritionStatus.ADEQUATE);
                    findings.add("MUAC " + muacCm + " cm is within the adequate range.");
                }
            } else {
                gaps.add("MUAC was recorded but is only interpretable between 6 and 59 months of age.");
            }
        } else {
            gaps.add("MUAC was not measured.");
        }

        if (weightForAgeZ != null) {
            anyInput = true;
            if (weightForAgeZ.compareTo(WAZ_SEVERE) < 0) {
                status = status.mostSevere(NutritionStatus.MODERATE_ACUTE_MALNUTRITION);
                findings.add("Weight-for-age z-score " + weightForAgeZ + " indicates severe underweight."
                        + " Underweight is not the same as wasting; confirm with weight-for-height or MUAC.");
            } else if (weightForAgeZ.compareTo(WAZ_MODERATE) < 0) {
                status = status.mostSevere(NutritionStatus.AT_RISK);
                findings.add("Weight-for-age z-score " + weightForAgeZ + " indicates underweight.");
            } else {
                status = status.mostSevere(NutritionStatus.ADEQUATE);
            }
        } else {
            gaps.add("Weight-for-age could not be scored.");
        }

        gaps.add("Weight-for-height, the defining indicator of wasting, is not yet available in this"
                + " deployment; this conclusion rests on MUAC and oedema.");

        if (!anyInput) {
            return new NutritionAssessment(NutritionStatus.NOT_ASSESSED, false,
                    List.of("No nutritional measurement was recorded, so nutritional status is unknown."),
                    gaps, CONTENT_VERSION);
        }

        boolean imamReferralIndicated = status.atLeastAsSevereAs(NutritionStatus.MODERATE_ACUTE_MALNUTRITION);
        return new NutritionAssessment(status, imamReferralIndicated, findings, gaps, CONTENT_VERSION);
    }

    public static boolean muacInterpretable(Integer ageDays) {
        return ageDays != null && ageDays >= MUAC_MIN_AGE_DAYS && ageDays <= MUAC_MAX_AGE_DAYS;
    }

    /** Bilateral pitting oedema: a finding that must distinguish "absent" from "not looked for". */
    public enum Oedema {
        PRESENT,
        ABSENT,
        NOT_ASSESSED
    }

    /**
     * @param imamReferralIndicated true when the child meets criteria for a nutrition programme
     * @param findings              what was found, in clinician-readable language
     * @param assessmentGaps        what could not be assessed and therefore limits the conclusion
     */
    public record NutritionAssessment(
            NutritionStatus status,
            boolean imamReferralIndicated,
            List<String> findings,
            List<String> assessmentGaps,
            String contentVersion) {
    }
}
