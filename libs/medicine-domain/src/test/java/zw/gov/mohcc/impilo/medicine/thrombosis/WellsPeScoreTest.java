package zw.gov.mohcc.impilo.medicine.thrombosis;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Wells PE score decides whether a D-dimer can exclude pulmonary embolism or whether imaging is
 * needed regardless. Each test names the harm the assertion stands between a patient and.
 */
class WellsPeScoreTest {

    @Test
    void aPatientWithNoCriteriaScoresZeroAndIsUnlikelyOnBothModels() {
        var result = WellsPeScore.of(EnumSet.noneOf(WellsPeScore.Criterion.class));

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.twoLevel()).isEqualTo(WellsPeScore.TwoLevelProbability.UNLIKELY);
        assertThat(result.threeLevel()).isEqualTo(WellsPeScore.ThreeLevelProbability.LOW);
        assertThat(result.contentVersion()).isEqualTo(WellsPeScore.CONTENT_VERSION);
    }

    @Test
    void theHalfPointWeightsSurviveAsHalfPoints() {
        // Three 1.5-point items total 4.5, which is above the two-level threshold of 4. An integer
        // total would round to 4 and report this patient as "PE unlikely", sending them down a
        // D-dimer pathway when the score says image them.
        var result = WellsPeScore.of(EnumSet.of(
                WellsPeScore.Criterion.HEART_RATE_OVER_100,
                WellsPeScore.Criterion.IMMOBILISATION_OR_RECENT_SURGERY,
                WellsPeScore.Criterion.PREVIOUS_PE_OR_DVT));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(4.5d));
        assertThat(result.twoLevel()).isEqualTo(WellsPeScore.TwoLevelProbability.LIKELY);
    }

    @Test
    void aSingleHalfPointItemIsNotRoundedAway() {
        var result = WellsPeScore.of(EnumSet.of(WellsPeScore.Criterion.HEART_RATE_OVER_100));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(1.5d));
        assertThat(result.score().scale()).isEqualTo(1);
    }

    @Test
    void theTwoModelsDisagreeAtTheBoundaryAndBothAreReported() {
        // Six points is LIKELY on the two-level model and only MODERATE on the three-level one.
        // Presenting one band without naming its model is how a score gets read against the wrong
        // decision rule.
        var result = WellsPeScore.of(EnumSet.of(
                WellsPeScore.Criterion.CLINICAL_SIGNS_OF_DVT,
                WellsPeScore.Criterion.PE_MOST_LIKELY_DIAGNOSIS));

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(6d));
        assertThat(result.twoLevel()).isEqualTo(WellsPeScore.TwoLevelProbability.LIKELY);
        assertThat(result.threeLevel()).isEqualTo(WellsPeScore.ThreeLevelProbability.MODERATE);
    }

    @Test
    void everyBandBoundaryFallsOnThePublishedNumber() {
        // Two-level splits above 4; three-level at 2 and above 6.
        assertThat(WellsPeScore.of(EnumSet.of(WellsPeScore.Criterion.HAEMOPTYSIS,
                        WellsPeScore.Criterion.MALIGNANCY)).threeLevel())
                .isEqualTo(WellsPeScore.ThreeLevelProbability.MODERATE);
        assertThat(WellsPeScore.of(EnumSet.of(WellsPeScore.Criterion.HAEMOPTYSIS)).threeLevel())
                .isEqualTo(WellsPeScore.ThreeLevelProbability.LOW);

        var four = WellsPeScore.of(EnumSet.of(WellsPeScore.Criterion.CLINICAL_SIGNS_OF_DVT,
                WellsPeScore.Criterion.HAEMOPTYSIS));
        assertThat(four.score()).isEqualByComparingTo(BigDecimal.valueOf(4d));
        assertThat(four.twoLevel()).isEqualTo(WellsPeScore.TwoLevelProbability.UNLIKELY);

        var sevenPointFive = WellsPeScore.of(EnumSet.of(
                WellsPeScore.Criterion.CLINICAL_SIGNS_OF_DVT,
                WellsPeScore.Criterion.PE_MOST_LIKELY_DIAGNOSIS,
                WellsPeScore.Criterion.HEART_RATE_OVER_100));
        assertThat(sevenPointFive.threeLevel())
                .isEqualTo(WellsPeScore.ThreeLevelProbability.HIGH);
    }

    @Test
    void anUnrecordedAssessmentIsRefusedRatherThanScoredAsAllNegative() {
        // A null set is "nobody assessed". Scoring it as zero reports an unexamined patient as low
        // probability, which is the reading that stops the investigation.
        var result = WellsPeScore.of(null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.score()).isNull();
        assertThat(result.twoLevel()).isNull();
        assertThat(result.threeLevel()).isNull();
    }

    @Test
    void theCriteriaThatContributedAreCarriedSoAClinicianCanCheckTheTotal() {
        var result = WellsPeScore.of(EnumSet.of(WellsPeScore.Criterion.HAEMOPTYSIS,
                WellsPeScore.Criterion.MALIGNANCY));

        assertThat(result.criteriaMet()).containsExactlyInAnyOrder(
                WellsPeScore.Criterion.HAEMOPTYSIS, WellsPeScore.Criterion.MALIGNANCY);
    }
}
