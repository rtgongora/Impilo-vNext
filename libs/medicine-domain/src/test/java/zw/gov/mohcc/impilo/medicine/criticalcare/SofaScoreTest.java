package zw.gov.mohcc.impilo.medicine.criticalcare;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOFA describes organ dysfunction across six systems, and a change in it is what the sepsis
 * definitions turn on. Each test names the harm the assertion stands between a patient and.
 */
class SofaScoreTest {

    @Test
    void aPhysiologicallyNormalPatientScoresZeroAcrossAllSixSystems() {
        var result = SofaScore.of(normal());

        assertThat(result.computed()).isTrue();
        assertThat(result.total()).isZero();
        assertThat(result.systemScores()).hasSize(6);
        assertThat(result.unscoredSystems()).isEmpty();
        assertThat(result.contentVersion()).isEqualTo(SofaScore.CONTENT_VERSION);
    }

    @Test
    void aMissingOrganSystemIsRefusedRatherThanScoredAsHealthy() {
        // The routine failure at a facility without blood gases: score five systems, call the result
        // a SOFA. A patient in respiratory failure who would score 4 is reported as scoring 0 for
        // that system, and the total says mild dysfunction in someone who is dying.
        var withoutGases = new SofaScore.Inputs(null, null,
                BigDecimal.valueOf(200d), BigDecimal.valueOf(15d), BigDecimal.valueOf(80d),
                SofaScore.VasopressorSupport.NONE, 15, BigDecimal.valueOf(80d),
                BigDecimal.valueOf(2000d));

        var result = SofaScore.of(withoutGases);

        assertThat(result.computed()).isFalse();
        assertThat(result.total()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.unscoredSystems()).anyMatch(s -> s.contains("Respiration"));
    }

    @Test
    void theSystemsThatCouldBeScoredAreStillReturnedWhenTheTotalIsRefused() {
        // Refusing the total is not refusing the information. Five real per-system scores remain
        // clinically useful and recordable; what is refused is the claim that they are a SOFA.
        var withoutGases = new SofaScore.Inputs(null, null,
                BigDecimal.valueOf(80d), BigDecimal.valueOf(40d), BigDecimal.valueOf(65d),
                SofaScore.VasopressorSupport.NONE, 13, BigDecimal.valueOf(200d),
                BigDecimal.valueOf(1200d));

        var result = SofaScore.of(withoutGases);

        assertThat(result.systemScores()).hasSize(5);
        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.COAGULATION, 2);
        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.CARDIOVASCULAR, 1);
        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.RENAL, 2);
        assertThat(result.systemScores()).doesNotContainKey(SofaScore.OrganSystem.RESPIRATION);
    }

    @Test
    void respiratoryPointsThreeAndFourRequireRespiratorySupport() {
        // A PaO₂/FiO₂ of 150 caps at 2 without support and reaches 3 with it. Awarding the higher
        // points to an unsupported patient overstates organ failure and, in a delta-based sepsis
        // rule, manufactures a deterioration that did not happen.
        var unsupported = SofaScore.of(withRatio(BigDecimal.valueOf(150d), false));
        var supported = SofaScore.of(withRatio(BigDecimal.valueOf(150d), true));

        assertThat(unsupported.systemScores()).containsEntry(SofaScore.OrganSystem.RESPIRATION, 2);
        assertThat(supported.systemScores()).containsEntry(SofaScore.OrganSystem.RESPIRATION, 3);
    }

    @Test
    void aRatioBelowTwoHundredWithoutAKnownSupportStatusLeavesRespirationUnscored() {
        // The support flag changes the score below 200, so an unanswered question cannot be read as
        // a no.
        var result = SofaScore.of(withRatio(BigDecimal.valueOf(150d), null));

        assertThat(result.computed()).isFalse();
        assertThat(result.unscoredSystems()).anyMatch(s -> s.contains("respiratory support"));
    }

    @Test
    void theRespiratoryBandsAboveTwoHundredDoNotDependOnSupport() {
        // Points 0 to 2 are available regardless, so a spontaneously breathing patient is scored
        // normally rather than being refused for want of a ventilator.
        var result = SofaScore.of(withRatio(BigDecimal.valueOf(250d), null));

        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.RESPIRATION, 2);
    }

    @Test
    void vasopressorSupportOverridesMeanArterialPressure() {
        // A patient whose pressure is normal only because they are on noradrenaline is not a
        // patient with a normal cardiovascular system. Scoring on the pressure alone reports the
        // treatment as though it were recovery.
        var normalPressureOnPressors = new SofaScore.Inputs(BigDecimal.valueOf(450d), false,
                BigDecimal.valueOf(200d), BigDecimal.valueOf(15d), BigDecimal.valueOf(85d),
                SofaScore.VasopressorSupport.HIGH_DOSE, 15, BigDecimal.valueOf(80d),
                BigDecimal.valueOf(2000d));

        var result = SofaScore.of(normalPressureOnPressors);

        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.CARDIOVASCULAR, 4);
    }

    @Test
    void anUnansweredVasopressorQuestionLeavesCardiovascularUnscored() {
        var result = SofaScore.of(new SofaScore.Inputs(BigDecimal.valueOf(450d), false,
                BigDecimal.valueOf(200d), BigDecimal.valueOf(15d), BigDecimal.valueOf(85d),
                null, 15, BigDecimal.valueOf(80d), BigDecimal.valueOf(2000d)));

        assertThat(result.computed()).isFalse();
        assertThat(result.unscoredSystems()).anyMatch(s -> s.contains("vasopressor"));
    }

    @Test
    void theRenalSystemTakesTheWorseOfCreatinineAndUrineOutput() {
        // Oliguria can precede a creatinine rise. Scoring on creatinine alone misses the patient
        // whose kidneys have already stopped.
        var oliguricWithNormalCreatinine = new SofaScore.Inputs(BigDecimal.valueOf(450d), false,
                BigDecimal.valueOf(200d), BigDecimal.valueOf(15d), BigDecimal.valueOf(85d),
                SofaScore.VasopressorSupport.NONE, 15, BigDecimal.valueOf(80d),
                BigDecimal.valueOf(150d));

        var result = SofaScore.of(oliguricWithNormalCreatinine);

        assertThat(result.systemScores()).containsEntry(SofaScore.OrganSystem.RENAL, 4);
    }

    @Test
    void aGlasgowComaScaleOutsideItsPossibleRangeLeavesTheNervousSystemUnscored() {
        // A GCS of 0 is the signature of an implementation that summed absent components. Scoring it
        // as the worst band would launder that error into the SOFA.
        var result = SofaScore.of(new SofaScore.Inputs(BigDecimal.valueOf(450d), false,
                BigDecimal.valueOf(200d), BigDecimal.valueOf(15d), BigDecimal.valueOf(85d),
                SofaScore.VasopressorSupport.NONE, 0, BigDecimal.valueOf(80d),
                BigDecimal.valueOf(2000d)));

        assertThat(result.computed()).isFalse();
        assertThat(result.unscoredSystems()).anyMatch(s -> s.contains("3 to 15"));
    }

    @Test
    void aMaximallyDysfunctionalPatientReachesTwentyFour() {
        var result = SofaScore.of(new SofaScore.Inputs(BigDecimal.valueOf(50d), true,
                BigDecimal.valueOf(10d), BigDecimal.valueOf(300d), BigDecimal.valueOf(40d),
                SofaScore.VasopressorSupport.HIGH_DOSE, 4, BigDecimal.valueOf(500d),
                BigDecimal.valueOf(50d)));

        assertThat(result.total()).isEqualTo(SofaScore.MAX_SCORE);
    }

    @Test
    void thereIsNoSeverityBandBecauseSofaIsReadAsAChangeFromBaseline() {
        // A band would invite an absolute reading the instrument does not support.
        assertThat(SofaScore.Scoring.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("severity")
                        || component.getName().toLowerCase().contains("band"));
    }

    private static SofaScore.Inputs normal() {
        return new SofaScore.Inputs(BigDecimal.valueOf(450d), false, BigDecimal.valueOf(200d),
                BigDecimal.valueOf(15d), BigDecimal.valueOf(85d), SofaScore.VasopressorSupport.NONE,
                15, BigDecimal.valueOf(80d), BigDecimal.valueOf(2000d));
    }

    private static SofaScore.Inputs withRatio(BigDecimal ratio, Boolean support) {
        return new SofaScore.Inputs(ratio, support, BigDecimal.valueOf(200d),
                BigDecimal.valueOf(15d), BigDecimal.valueOf(85d), SofaScore.VasopressorSupport.NONE,
                15, BigDecimal.valueOf(80d), BigDecimal.valueOf(2000d));
    }
}
