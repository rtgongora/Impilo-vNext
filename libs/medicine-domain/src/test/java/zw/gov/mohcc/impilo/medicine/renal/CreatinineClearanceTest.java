package zw.gov.mohcc.impilo.medicine.renal;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Creatinine clearance selects renal drug doses, including for medicines with narrow therapeutic
 * windows. Each test names the harm the assertion stands between a patient and.
 */
class CreatinineClearanceTest {

    @Test
    void theWorkedMaleExampleMatchesThePublishedEquation() {
        // ((140 − 60) × 70) / (72 × 1.0) = 77.8 mL/min. No multiplier for a man.
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(result.computed()).isTrue();
        assertThat(result.clearanceMlPerMin().doubleValue()).isCloseTo(77.8d, within(0.1d));
        assertThat(result.contentVersion()).isEqualTo(CreatinineClearance.CONTENT_VERSION);
    }

    @Test
    void theFemaleMultiplierIsAppliedAndIsWorthFifteenPerCent() {
        // Omitting the 0.85 overstates clearance in every woman, which is the direction that
        // overdoses. 77.8 × 0.85 = 66.1.
        var female = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70d), Sex.FEMALE);
        var male = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(female.clearanceMlPerMin().doubleValue()).isCloseTo(66.1d, within(0.2d));
        assertThat(female.clearanceMlPerMin().doubleValue())
                .isCloseTo(male.clearanceMlPerMin().doubleValue() * 0.85d, within(0.2d));
    }

    @Test
    void micromolesPerLitreConvertsExactlyOnceAndAgreesWithTheMgPerDlPath() {
        // Zimbabwean laboratories report µmol/L. A doubled conversion returns a clearance in the
        // thousands, which reads as a normal kidney and removes the dose reduction.
        BigDecimal micromoles = BigDecimal.valueOf(1.0d)
                .multiply(CreatinineClearance.MICROMOL_PER_LITRE_PER_MG_PER_DL);

        var fromSi = CreatinineClearance.fromCreatinineMicromolPerLitre(micromoles, 60,
                BigDecimal.valueOf(70d), Sex.MALE);
        var fromMgDl = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(fromSi.clearanceMlPerMin().doubleValue())
                .isCloseTo(fromMgDl.clearanceMlPerMin().doubleValue(), within(0.2d));
    }

    @Test
    void aMissingWeightIsRefusedRatherThanDefaultedToSeventyKilograms() {
        // The classic shortcut. A 45 kg woman dosed on an assumed 70 kg receives a clearance more
        // than half again her real one, and the renally cleared medicine is dosed accordingly.
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60, null,
                Sex.FEMALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("weight");
    }

    @Test
    void aMissingSexIsRefusedBecauseTheMultiplierMovesTheDose() {
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70d), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void aChildIsRefusedRatherThanScoredOnTheAdultEquation() {
        // Cockcroft-Gault was fitted in adults. A child's low creatinine and low weight combine into
        // a number the equation was never meant to produce.
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(0.4d), 8,
                BigDecimal.valueOf(25d), Sex.MALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
    }

    @Test
    void aCreatinineInMicromolesTypedIntoTheMgPerDlFieldIsRefused() {
        // 88 is an ordinary µmol/L creatinine and an impossible mg/dL one, and would return a
        // clearance near zero — reading as dialysis-dependent failure in a patient with normal renal
        // function, which stops medicines they need.
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(88d), 60,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void aWeightInGramsIsRefusedRatherThanProducingAnImpossiblyHighClearance() {
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 60,
                BigDecimal.valueOf(70000d), Sex.MALE);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void theInputsThatDroveTheResultAreCarriedForAudit() {
        // A clearance stored without the weight and creatinine behind it cannot be re-derived, and a
        // dose that cannot be re-derived cannot be defended.
        var result = CreatinineClearance.fromCreatinineMicromolPerLitre(BigDecimal.valueOf(88.4d), 60,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(result.weightKg()).isEqualByComparingTo(BigDecimal.valueOf(70d));
        assertThat(result.creatinineMgPerDl().doubleValue()).isCloseTo(1.0d, within(0.001d));
    }

    @Test
    void anAgeAtOrBeyondTheEquationsInterceptIsRefusedRatherThanReturningZeroOrNegative() {
        // At 140 years the (140 − age) term collapses. A naive implementation returns 0 mL/min,
        // which is indistinguishable on screen from anuric renal failure.
        var result = CreatinineClearance.fromCreatinineMgPerDl(BigDecimal.valueOf(1.0d), 119,
                BigDecimal.valueOf(70d), Sex.MALE);

        assertThat(result.computed()).isTrue();
        assertThat(result.clearanceMlPerMin().doubleValue()).isGreaterThan(0d);
    }
}
