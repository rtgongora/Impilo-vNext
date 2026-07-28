package zw.gov.mohcc.impilo.medicine.cardio;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;
import zw.gov.mohcc.impilo.medicine.common.Sex;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This suite guards two different things.
 *
 * <p>The first is the model's <em>shape</em>: monotone in every risk factor, correct at the two
 * corners of the chart that are qualitatively unambiguous, and refusing outside the published age
 * rows. Those properties are real and testable even though the calibration is not WHO's.</p>
 *
 * <p>The second, and more important, is the <em>honesty machinery</em>. This calculator is not the
 * WHO chart, and the tests below assert that it can never present itself as one: no percentage
 * exists anywhere in the API, every result is stamped as a seed-model estimate, and every result
 * carries the caveat a surface must display. If someone later vendors the real chart, these are the
 * tests that should fail and force the flags to be updated with it.</p>
 */
class CvdRiskCalculatorTest {

    private static final BigDecimal OPTIMAL_SYSTOLIC = BigDecimal.valueOf(110);
    private static final BigDecimal SEVERE_SYSTOLIC = BigDecimal.valueOf(190);
    private static final BigDecimal OPTIMAL_CHOLESTEROL = BigDecimal.valueOf(3.5d);
    private static final BigDecimal HIGH_CHOLESTEROL = BigDecimal.valueOf(7.5d);

    @Test
    void theLowestCornerOfTheChartIsTheLowestBand() {
        // A 40-year-old non-smoking, non-diabetic woman with optimal blood pressure and cholesterol
        // is in the lowest band on every published version of the chart. This is one of only two
        // cells the seed model is calibrated to, so it is a genuine anchor and not a tautology.
        var result = CvdRiskCalculator.laboratory(40, Sex.FEMALE, false, false,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false);

        assertThat(result.computed()).isTrue();
        assertThat(result.band()).isEqualTo(CvdRiskBand.BELOW_5);
        assertThat(result.points()).isZero();
    }

    @Test
    void theHighestCornerOfTheChartIsTheHighestBand() {
        // A 72-year-old male smoker with diabetes, severe hypertension and the top cholesterol
        // column is in the highest band on every published version. The other calibration anchor.
        var result = CvdRiskCalculator.laboratory(72, Sex.MALE, true, true,
                SEVERE_SYSTOLIC, HIGH_CHOLESTEROL, false);

        assertThat(result.computed()).isTrue();
        assertThat(result.band()).isEqualTo(CvdRiskBand.THIRTY_OR_MORE);
        assertThat(result.points()).isEqualTo(CvdRiskCalculator.LABORATORY_MAX_POINTS);
    }

    @Test
    void everyRiskFactorMovesTheScoreUpwardsAndNeverDownwards() {
        // Monotonicity is the one property a risk model must have even when it is uncalibrated. If
        // adding a risk factor could lower someone's band, the model would tell a clinician that
        // starting smoking was protective — a wrong direction is a different class of error from a
        // wrong magnitude, and it is the class that destroys trust in the whole tool.
        var baseline = CvdRiskCalculator.laboratory(55, Sex.FEMALE, false, false,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false);

        assertThat(CvdRiskCalculator.laboratory(60, Sex.FEMALE, false, false,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
        assertThat(CvdRiskCalculator.laboratory(55, Sex.MALE, false, false,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
        assertThat(CvdRiskCalculator.laboratory(55, Sex.FEMALE, true, false,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
        assertThat(CvdRiskCalculator.laboratory(55, Sex.FEMALE, false, true,
                OPTIMAL_SYSTOLIC, OPTIMAL_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
        assertThat(CvdRiskCalculator.laboratory(55, Sex.FEMALE, false, false,
                SEVERE_SYSTOLIC, OPTIMAL_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
        assertThat(CvdRiskCalculator.laboratory(55, Sex.FEMALE, false, false,
                OPTIMAL_SYSTOLIC, HIGH_CHOLESTEROL, false).points())
                .isGreaterThan(baseline.points());
    }

    @Test
    void bandFloorsAreStrictlyAscendingOnBothVariants() {
        // A non-ascending floor list would make the band lookup return a lower band for a higher
        // score, silently, for one narrow range of points. Asserted structurally rather than by
        // sampling, so no patient profile has to be lucky enough to hit the broken range.
        assertThat(CvdRiskCalculator.LABORATORY_BAND_FLOORS).isSorted();
        assertThat(CvdRiskCalculator.NON_LABORATORY_BAND_FLOORS).isSorted();
        assertThat(Arrays.stream(CvdRiskCalculator.LABORATORY_BAND_FLOORS).distinct().count())
                .isEqualTo(CvdRiskCalculator.LABORATORY_BAND_FLOORS.length);
        assertThat(CvdRiskCalculator.LABORATORY_BAND_FLOORS)
                .hasSameSizeAs(CvdRiskBand.values());
    }

    @Test
    void theNonLaboratoryChartHasNoDiabetesTermAtAll() {
        // The WHO publishes the non-laboratory chart for the clinic with no laboratory, which is
        // most primary-care contacts in Zimbabwe. Diabetes is omitted because diagnosing it needs
        // the laboratory the chart exists to do without. A diabetes parameter appearing on this
        // signature would mean someone had "improved" the chart into something WHO did not publish.
        assertThat(Arrays.stream(CvdRiskCalculator.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("nonLaboratory"))
                .findFirst()
                .orElseThrow()
                .getParameterCount())
                .isEqualTo(6);
    }

    @Test
    void theTwoChartsAgreeOnTheSamePatientWhenBothCanBeEntered() {
        // A middle-aged smoker with moderate hypertension should not land two bands apart depending
        // on which chart the clinic could afford to run. Exact agreement is not expected of the real
        // charts either, but a gross disagreement here would mean the two seed scales are wrongly
        // normalised against each other.
        var laboratory = CvdRiskCalculator.laboratory(55, Sex.MALE, true, false,
                BigDecimal.valueOf(145), BigDecimal.valueOf(5.5d), false);
        var nonLaboratory = CvdRiskCalculator.nonLaboratory(55, Sex.MALE, true,
                BigDecimal.valueOf(145), BigDecimal.valueOf(27), false);

        assertThat(laboratory.band()).isEqualTo(nonLaboratory.band());
    }

    @Test
    void everyResultIsStampedAsASeedModelAndCarriesTheCaveat() {
        // The honesty machinery. A surface that renders a band without this sentence is presenting
        // an Impilo estimate as a WHO chart reading, which is the specific misrepresentation this
        // whole class was written to avoid.
        var result = CvdRiskCalculator.nonLaboratory(60, Sex.MALE, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(28), false);

        assertThat(CvdRiskCalculator.CHART_CELLS_VENDORED).isFalse();
        assertThat(result.basis()).isEqualTo(CvdRiskCalculator.Basis.IMPILO_ENGINEERING_SEED_MODEL);
        assertThat(result.requiresSeedCaveat()).isTrue();
        assertThat(result.caveat()).contains("not read from a WHO chart");
        assertThat(result.whoRegion()).isEqualTo("AFR-E");
    }

    @Test
    void thereIsNoWayToGetAPercentageOutOfThisPackage() {
        // The field this library was written to replace, cvdRisk10yrPercent, is a number typed in
        // by a clinician. It must stay that way until the WHO chart cells are vendored: a computed
        // "23%" is indistinguishable in a record from a real chart reading, and once stored it is
        // permanent. This test fails the moment someone adds a percent accessor.
        assertThat(CvdRiskCalculator.Result.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("percent"));
        assertThat(CvdRiskBand.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("percent"));
    }

    @Test
    void someoneWithEstablishedCardiovascularDiseaseIsRefused() {
        // The charts estimate the 10-year risk of a FIRST cardiovascular event. Someone who has
        // already had one is not at risk of a first, and scoring them produces a reassuring band
        // for the highest-risk patient in the clinic.
        var result = CvdRiskCalculator.laboratory(60, Sex.MALE, false, false,
                BigDecimal.valueOf(130), BigDecimal.valueOf(4.5d), true);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INSTRUMENT_NOT_APPLICABLE);
        assertThat(result.explanation()).contains("first cardiovascular event");
    }

    @Test
    void ageOutsideTheFortyToSeventyFourRowsIsRefusedRatherThanExtrapolated() {
        // Below 40 the chart is not published, and a 10-year estimate would be low in almost
        // everyone while hiding a high lifetime risk — a 35-year-old smoker with a cholesterol of 8
        // needs treating and would be banded reassuringly. Above 74 the model was never fitted.
        var tooYoung = CvdRiskCalculator.laboratory(35, Sex.MALE, true, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(8.0d), false);
        var tooOld = CvdRiskCalculator.laboratory(80, Sex.MALE, false, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(5.0d), false);

        assertThat(tooYoung.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
        assertThat(tooYoung.explanation()).contains("lifetime risk");
        assertThat(tooOld.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
    }

    @Test
    void unrecordedSmokingStatusIsRefusedRatherThanReadAsNonSmoking() {
        // Most people are non-smokers, so defaulting looks harmless and is the single change that
        // would move the most patients down a band. "Not asked" is a task; "non-smoker" is a fact.
        var result = CvdRiskCalculator.laboratory(60, Sex.MALE, null, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(6.0d), false);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("not assumed");
    }

    @Test
    void unrecordedDiabetesIsRefusedOnTheLaboratoryChartOnly() {
        // The laboratory chart is printed as two panels, with and without diabetes; there is no
        // cell to read without knowing which panel. The non-laboratory chart has no such panel, so
        // the same patient can still be scored there — which is the correct fallback and is exactly
        // why the two variants exist as separate instruments.
        var laboratory = CvdRiskCalculator.laboratory(60, Sex.MALE, false, null,
                BigDecimal.valueOf(150), BigDecimal.valueOf(6.0d), false);
        var nonLaboratory = CvdRiskCalculator.nonLaboratory(60, Sex.MALE, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(26), false);

        assertThat(laboratory.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(nonLaboratory.computed()).isTrue();
    }

    @Test
    void aDiastolicReadingTypedIntoTheSystolicFieldIsRefused() {
        // 60 mmHg is an ordinary diastolic and an unsurvivable systolic. Left to run it would band
        // the patient in the lowest blood-pressure column, which is the wrong direction.
        var result = CvdRiskCalculator.laboratory(60, Sex.MALE, false, false,
                BigDecimal.valueOf(60), BigDecimal.valueOf(5.0d), false);

        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("diastolic");
    }

    @Test
    void cholesterolInMilligramsPerDecilitreIsRefused() {
        // 200 mg/dL is a common total cholesterol and an impossible mmol/L value. Left to run it
        // would put every such patient in the top cholesterol column.
        var result = CvdRiskCalculator.laboratory(60, Sex.MALE, false, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(200), false);

        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("mg/dL");
    }

    @Test
    void anImplausibleBodyMassIndexIsRefusedOnTheNonLaboratoryChart() {
        // A height entered in metres rather than centimetres produces a BMI in the thousands. The
        // guard catches it before it reaches the top BMI column.
        var result = CvdRiskCalculator.nonLaboratory(60, Sex.MALE, false,
                BigDecimal.valueOf(150), BigDecimal.valueOf(900), false);

        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("height");
    }

    @Test
    void bmiIsComputedFromWeightAndHeightOrNotAtAll() {
        // 70 kg at 175 cm is 22.9. A missing measurement yields null rather than a defaulted body,
        // so the caller is forced back to the missing-input refusal instead of scoring a phantom.
        assertThat(CvdRiskCalculator.bmi(BigDecimal.valueOf(70), BigDecimal.valueOf(175)).doubleValue())
                .isEqualTo(22.9d);
        assertThat(CvdRiskCalculator.bmi(null, BigDecimal.valueOf(175))).isNull();
        assertThat(CvdRiskCalculator.bmi(BigDecimal.valueOf(70), BigDecimal.ZERO)).isNull();
    }

    @Test
    void chartColumnBoundariesFallOnThePublishedEdges() {
        // The columns are the one part of the chart that is unambiguously published, so an
        // off-by-one here is a real error even though the cell values are not ours. 139 and 140
        // are different columns; 4.9 and 5.0 are different columns.
        assertThat(CvdRiskCalculator.systolicColumn(119.9d)).isZero();
        assertThat(CvdRiskCalculator.systolicColumn(120d)).isEqualTo(1);
        assertThat(CvdRiskCalculator.systolicColumn(139.9d)).isEqualTo(1);
        assertThat(CvdRiskCalculator.systolicColumn(140d)).isEqualTo(2);
        assertThat(CvdRiskCalculator.systolicColumn(180d)).isEqualTo(4);

        assertThat(CvdRiskCalculator.cholesterolColumn(3.99d)).isZero();
        assertThat(CvdRiskCalculator.cholesterolColumn(4.0d)).isEqualTo(1);
        assertThat(CvdRiskCalculator.cholesterolColumn(4.9d)).isEqualTo(1);
        assertThat(CvdRiskCalculator.cholesterolColumn(5.0d)).isEqualTo(2);
        assertThat(CvdRiskCalculator.cholesterolColumn(7.0d)).isEqualTo(4);

        assertThat(CvdRiskCalculator.bmiColumn(19.9d)).isZero();
        assertThat(CvdRiskCalculator.bmiColumn(20d)).isEqualTo(1);
        assertThat(CvdRiskCalculator.bmiColumn(30d)).isEqualTo(3);
        assertThat(CvdRiskCalculator.bmiColumn(35d)).isEqualTo(3);
        assertThat(CvdRiskCalculator.bmiColumn(35.1d)).isEqualTo(4);
    }

    @Test
    void bandsAreOrderedAndComparable() {
        assertThat(CvdRiskBand.THIRTY_OR_MORE.atLeastAsHighAs(CvdRiskBand.BELOW_5)).isTrue();
        assertThat(CvdRiskBand.BELOW_5.atLeastAsHighAs(CvdRiskBand.FIVE_TO_UNDER_10)).isFalse();
        assertThat(CvdRiskBand.TEN_TO_UNDER_20.atLeastAsHighAs(null)).isFalse();
    }
}
