package zw.gov.mohcc.impilo.medicine.hepatic;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MELD orders a transplant waiting list. A score that is two points low moves a patient down that
 * list. Each test names the harm the assertion stands between a patient and.
 */
class MeldScoreTest {

    @Test
    void aPatientWithEntirelyNormalLabsSitsAtTheFloorRatherThanBelowIt() {
        // Bilirubin 0.4, INR 0.9 and creatinine 0.8 all fall under 1.0. Without the flooring rule
        // every logarithm is negative and the arithmetic returns a score in the low single digits or
        // below — a number that says this liver is healthier than the model's healthiest liver.
        var result = MeldScore.of(BigDecimal.valueOf(0.4d), BigDecimal.valueOf(0.8d),
                BigDecimal.valueOf(0.9d), false);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isEqualTo(MeldScore.MIN_SCORE);
        assertThat(result.contentVersion()).isEqualTo(MeldScore.CONTENT_VERSION);
    }

    @Test
    void aDecompensatedPatientScoresInTheHighTwentiesRatherThanBeingClamped() {
        // Bilirubin 8 mg/dL, creatinine 2.0, INR 2.3: a genuinely sick liver that must land well
        // inside the range, proving the clamp is not masking an arithmetic error.
        var result = MeldScore.of(BigDecimal.valueOf(8d), BigDecimal.valueOf(2.0d),
                BigDecimal.valueOf(2.3d), false);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isBetween(24, 32);
        assertThat(result.clampedToPublishedRange()).isFalse();
    }

    @Test
    void dialysisForcesTheCreatinineToTheCapAndRaisesTheScore() {
        // A dialysed patient's post-dialysis creatinine reflects the machine, not the kidney. Taking
        // it at face value understates MELD in exactly the patients who are sickest, which is the
        // direction that costs a transplant place.
        BigDecimal postDialysisCreatinine = BigDecimal.valueOf(1.1d);

        var withoutFlag = MeldScore.of(BigDecimal.valueOf(3d), postDialysisCreatinine,
                BigDecimal.valueOf(1.5d), false);
        var withFlag = MeldScore.of(BigDecimal.valueOf(3d), postDialysisCreatinine,
                BigDecimal.valueOf(1.5d), true);

        assertThat(withFlag.score()).isGreaterThan(withoutFlag.score());
        assertThat(withFlag.dialysisSubstitutionApplied()).isTrue();
        assertThat(withFlag.creatinineUsedMgPerDl())
                .isEqualByComparingTo(MeldScore.CREATININE_CAP_MG_PER_DL);
    }

    @Test
    void aCreatinineAboveTheCapIsHeldAtTheCap() {
        // The model does not let renal failure dominate the hepatic score without limit.
        var atCap = MeldScore.of(BigDecimal.valueOf(3d), BigDecimal.valueOf(4.0d),
                BigDecimal.valueOf(1.5d), false);
        var aboveCap = MeldScore.of(BigDecimal.valueOf(3d), BigDecimal.valueOf(9.0d),
                BigDecimal.valueOf(1.5d), false);

        assertThat(aboveCap.score()).isEqualTo(atCap.score());
        assertThat(aboveCap.creatinineUsedMgPerDl())
                .isEqualByComparingTo(MeldScore.CREATININE_CAP_MG_PER_DL);
    }

    @Test
    void anUnansweredDialysisQuestionIsRefusedRatherThanReadAsNo() {
        // "Not asked" and "not dialysed" produce different scores. Treating the first as the second
        // is a silent understatement in the sickest patients.
        var result = MeldScore.of(BigDecimal.valueOf(3d), BigDecimal.valueOf(1.1d),
                BigDecimal.valueOf(1.5d), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("dialys");
    }

    @Test
    void aPartialMeldIsRefusedOnEveryMissingTerm() {
        assertThat(MeldScore.of(null, BigDecimal.ONE, BigDecimal.ONE, false).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(MeldScore.of(BigDecimal.ONE, null, BigDecimal.ONE, false).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(MeldScore.of(BigDecimal.ONE, BigDecimal.ONE, null, false).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void siUnitsConvertOnceAndAgreeWithTheMgPerDlPath() {
        // Zimbabwean laboratories report µmol/L. A missed or doubled conversion moves the score by
        // more than the width of several waiting-list bands.
        var fromSi = MeldScore.fromSiUnits(
                BigDecimal.valueOf(8d).multiply(MeldScore.MICROMOL_PER_LITRE_PER_MG_PER_DL_BILIRUBIN),
                BigDecimal.valueOf(2d).multiply(MeldScore.MICROMOL_PER_LITRE_PER_MG_PER_DL_CREATININE),
                BigDecimal.valueOf(2.3d), false);
        var fromMgDl = MeldScore.of(BigDecimal.valueOf(8d), BigDecimal.valueOf(2d),
                BigDecimal.valueOf(2.3d), false);

        assertThat(fromSi.score()).isEqualTo(fromMgDl.score());
    }

    @Test
    void aBilirubinInMicromolesTypedIntoTheMgPerDlFieldIsRefused() {
        // 137 µmol/L is an ordinary jaundiced bilirubin and an impossible mg/dL one.
        var result = MeldScore.of(BigDecimal.valueOf(137d), BigDecimal.valueOf(1.0d),
                BigDecimal.valueOf(1.2d), false);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void theScoreNeverLeavesThePublishedRange() {
        var extreme = MeldScore.of(BigDecimal.valueOf(90d), BigDecimal.valueOf(20d),
                BigDecimal.valueOf(19d), true);

        assertThat(extreme.score()).isBetween(MeldScore.MIN_SCORE, MeldScore.MAX_SCORE);
        assertThat(extreme.clampedToPublishedRange()).isTrue();
    }
}
