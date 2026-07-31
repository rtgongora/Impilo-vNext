package zw.gov.mohcc.impilo.medicine.cardio;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHA₂DS₂-VASc informs whether a patient with atrial fibrillation is offered lifelong
 * anticoagulation. Each test names the harm the assertion stands between a patient and.
 */
class Cha2ds2VascScoreTest {

    @Test
    void aSeventyYearOldManWithHypertensionScoresTwo() {
        // Age 65–74 is one point, hypertension one.
        var result = Cha2ds2VascScore.of(70, Sex.MALE,
                EnumSet.of(Cha2ds2VascScore.Criterion.HYPERTENSION));

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isEqualTo(2);
        assertThat(result.agePoints()).isEqualTo(1);
        assertThat(result.sexPoints()).isZero();
        assertThat(result.contentVersion()).isEqualTo(Cha2ds2VascScore.CONTENT_VERSION);
    }

    @Test
    void priorStrokeIsWeightedDouble() {
        // An 80-year-old woman with a prior stroke: age 2, stroke 2, sex 1.
        var result = Cha2ds2VascScore.of(80, Sex.FEMALE,
                EnumSet.of(Cha2ds2VascScore.Criterion.PRIOR_STROKE_TIA_OR_THROMBOEMBOLISM));

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.agePoints()).isEqualTo(2);
    }

    @Test
    void theScoreExcludingSexIsReportedSoAWomansSinglePointIsNotMisread() {
        // The guidance is explicit that female sex alone does not justify anticoagulation: a woman
        // scoring 1 from sex carries the risk of a man scoring 0. A bare total of 1 reads as an
        // acquired risk factor and starts a conversation about lifelong anticoagulation that the
        // evidence does not support.
        var woman = Cha2ds2VascScore.of(50, Sex.FEMALE,
                EnumSet.noneOf(Cha2ds2VascScore.Criterion.class));
        var man = Cha2ds2VascScore.of(50, Sex.MALE,
                EnumSet.noneOf(Cha2ds2VascScore.Criterion.class));

        assertThat(woman.score()).isEqualTo(1);
        assertThat(woman.scoreExcludingSex()).isZero();
        assertThat(woman.scoreExcludingSex()).isEqualTo(man.score());
    }

    @Test
    void theAgeBandsFallOnThePublishedBoundaries() {
        // Three bands, not one threshold. An off-by-one at 65 or 75 changes the recommendation for
        // an entire birth year of patients.
        assertThat(agePointsAt(64)).isZero();
        assertThat(agePointsAt(65)).isEqualTo(1);
        assertThat(agePointsAt(74)).isEqualTo(1);
        assertThat(agePointsAt(75)).isEqualTo(2);
    }

    private static int agePointsAt(int age) {
        return Cha2ds2VascScore.of(age, Sex.MALE, EnumSet.noneOf(Cha2ds2VascScore.Criterion.class))
                .agePoints();
    }

    @Test
    void theMaximumIsNine() {
        var result = Cha2ds2VascScore.of(80, Sex.FEMALE, EnumSet.allOf(Cha2ds2VascScore.Criterion.class));

        assertThat(result.score()).isEqualTo(Cha2ds2VascScore.MAX_SCORE);
    }

    @Test
    void anUnrecordedHistoryIsRefusedRatherThanScoredAsNoRiskFactors() {
        // A null set means nobody took the history. Scoring it as zero reports an unassessed patient
        // as low risk, which is the reading that withholds anticoagulation.
        var result = Cha2ds2VascScore.of(70, Sex.MALE, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void missingAgeOrSexIsRefusedBecauseBothAreScoredItems() {
        assertThat(Cha2ds2VascScore.of(null, Sex.MALE,
                EnumSet.noneOf(Cha2ds2VascScore.Criterion.class)).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(Cha2ds2VascScore.of(70, null,
                EnumSet.noneOf(Cha2ds2VascScore.Criterion.class)).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void aChildIsRefusedRatherThanScoredOnAnAdultInstrument() {
        var result = Cha2ds2VascScore.of(10, Sex.MALE,
                EnumSet.noneOf(Cha2ds2VascScore.Criterion.class));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INSTRUMENT_NOT_APPLICABLE);
    }
}
