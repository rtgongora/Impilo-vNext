package zw.gov.mohcc.impilo.medicine.renal;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * eGFR decides who is referred, who is transplant-listed and what dose of a renally cleared medicine
 * is safe. Each test below names the harm the assertion is standing between a patient and.
 */
class EgfrCalculatorTest {

    @Test
    void sixtyYearOldWomanWithCreatinineOnePointTwoIsAroundFiftyTwo() {
        // The worked example for the female branch. κ = 0.7 and creatinine 1.2 mg/dL puts Scr/κ
        // above 1, so the −1.200 exponent applies and the 1.012 female multiplier is added:
        // 142 × 1.714^−1.200 × 0.9938^60 × 1.012 = 51.8. Published calculators give 52.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), 60, Sex.FEMALE);

        assertThat(result.computed()).isTrue();
        assertThat(result.egfr().doubleValue()).isCloseTo(51.8d, within(0.2d));
        assertThat(result.kdigoStage()).isEqualTo(KdigoStage.G3A);
        assertThat(result.reason()).isNull();
        assertThat(result.contentVersion()).isEqualTo(EgfrCalculator.CONTENT_VERSION);
    }

    @Test
    void sixtyYearOldManWithCreatinineZeroPointNineSitsExactlyOnTheKappaBreakpoint() {
        // The male worked example, chosen because creatinine 0.9 equals κ exactly, so both power
        // terms collapse to 1 and the result is 142 × 0.9938^60 = 97.8. It is the one input where
        // an error in either exponent cannot hide behind the other.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(0.9d), 60, Sex.MALE);

        assertThat(result.computed()).isTrue();
        assertThat(result.egfr().doubleValue()).isCloseTo(97.8d, within(0.2d));
        assertThat(result.kdigoStage()).isEqualTo(KdigoStage.G1);
    }

    @Test
    void micromolesPerLitreConvertsExactlyOnceAndAgreesWithTheMgPerDlPath() {
        // Zimbabwean laboratories report µmol/L; the equation is in mg/dL. A double conversion —
        // dividing by 88.4 twice — returns an eGFR in the hundreds for a patient in established
        // renal failure, which reads as a normal kidney and stops the referral.
        BigDecimal micromoles = BigDecimal.valueOf(1.2d).multiply(BigDecimal.valueOf(88.4d));

        var fromMicromoles = EgfrCalculator.fromCreatinineMicromolPerLitre(micromoles, 60, Sex.FEMALE);
        var fromMgPerDl = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), 60, Sex.FEMALE);

        assertThat(fromMicromoles.computed()).isTrue();
        assertThat(fromMicromoles.egfr().doubleValue())
                .isCloseTo(fromMgPerDl.egfr().doubleValue(), within(0.1d));
    }

    @Test
    void thereIsNoRaceCoefficientAnywhereInTheResult() {
        // The 2009 equation multiplied Black patients' eGFR by 1.159, raising it and so delaying
        // referral, transplant listing and dose reduction. The 2021 refit removed it. This test
        // exists as a tripwire: the same patient must produce the same number regardless of any
        // demographic the caller might be tempted to pass, and the API offers nowhere to pass one.
        var first = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.5d), 45, Sex.MALE);
        var second = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.5d), 45, Sex.MALE);

        assertThat(first.egfr()).isEqualByComparingTo(second.egfr());
        assertThat(EgfrCalculator.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("race")
                        || field.getName().toLowerCase().contains("ethnic"));
    }

    @Test
    void missingCreatinineIsRefusedRatherThanTreatedAsNormal() {
        // The central safety property. An eGFR invented from an absent creatinine looks exactly
        // like a healthy kidney, and a clinician acts on it by not acting.
        var result = EgfrCalculator.fromCreatinineMgPerDl(null, 60, Sex.FEMALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.egfr()).isNull();
        assertThat(result.kdigoStage()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("creatinine");
    }

    @Test
    void missingSexIsRefusedBecauseDefaultingItCrossesAKdigoBoundary() {
        // κ and α are sex-specific and the female branch also carries a 1.012 multiplier. Guessing
        // moves the result by roughly ten per cent, which is enough to move a patient between G3a
        // and G3b — different monitoring intervals, different medicine doses.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), 60, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("Sex");
    }

    @Test
    void missingAgeIsRefusedBecauseTheEquationHasAnAgeTerm() {
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), null, Sex.FEMALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void aChildIsRefusedRatherThanScoredOnTheAdultEquation() {
        // CKD-EPI 2021 was fitted in adults. A ten-year-old has a much lower creatinine because
        // they have much less muscle, and the adult equation reads that as a superb kidney. The
        // paediatric equations (CKiD U25, bedside Schwartz) are a different curve and are not here.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(0.4d), 10, Sex.MALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
        assertThat(result.explanation()).contains("Schwartz");
    }

    @Test
    void aCreatinineInMicromolesTypedIntoTheMgPerDlFieldIsRefused() {
        // 106 is an ordinary µmol/L creatinine and an impossible mg/dL one. Left to run, the
        // equation returns an eGFR under 1, which reads as a dialysis patient. This is the most
        // common data-entry fault in a laboratory that reports in SI units.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(106d), 60, Sex.FEMALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("µmol/L");
    }

    @Test
    void aZeroOrNegativeCreatinineIsRefusedOnBothEntryPoints() {
        assertThat(EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.ZERO, 60, Sex.FEMALE).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(EgfrCalculator.fromCreatinineMicromolPerLitre(BigDecimal.valueOf(-5d), 60, Sex.FEMALE)
                .reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void anImplausibleAgeIsRefusedRatherThanScored() {
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), 199, Sex.FEMALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void kdigoBoundariesFallOnThePublishedNumbers() {
        // The boundaries decide monitoring frequency and dose adjustment, so an off-by-one at any
        // of them moves real patients. 59.9 must not read as G2 and 89.9 must not read as G1.
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(90.0d))).isEqualTo(KdigoStage.G1);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(89.9d))).isEqualTo(KdigoStage.G2);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(60.0d))).isEqualTo(KdigoStage.G2);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(59.9d))).isEqualTo(KdigoStage.G3A);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(45.0d))).isEqualTo(KdigoStage.G3A);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(44.9d))).isEqualTo(KdigoStage.G3B);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(30.0d))).isEqualTo(KdigoStage.G3B);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(29.9d))).isEqualTo(KdigoStage.G4);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(15.0d))).isEqualTo(KdigoStage.G4);
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(14.9d))).isEqualTo(KdigoStage.G5);
    }

    @Test
    void aMissingEgfrHasNoKdigoCategoryRatherThanTheWorstOne() {
        // Null must not render as G5. A missing result and established kidney failure are opposite
        // clinical situations and must never share a display.
        assertThat(KdigoStage.fromEgfr(null)).isNull();
        assertThat(KdigoStage.fromEgfr(BigDecimal.valueOf(-1d))).isNull();
    }

    @Test
    void theCategoryAlwaysMatchesTheNumberThatWasPublished() {
        // The category is derived from the rounded value the result carries, not from the raw
        // double. Otherwise a stored eGFR of 60.0 could sit beside a stored category of G3a and
        // nobody could tell which of the two was wrong.
        var result = EgfrCalculator.fromCreatinineMgPerDl(BigDecimal.valueOf(1.2d), 60, Sex.FEMALE);

        assertThat(result.kdigoStage()).isEqualTo(KdigoStage.fromEgfr(result.egfr()));
    }
}
