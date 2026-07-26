package zw.gov.mohcc.impilo.paediatrics.growth;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthEngineTest {

    private final GrowthEngine engine = new GrowthEngine();

    @Test
    void medianMeasurementsScoreAtZeroOnEveryIndicator() {
        // WHO male day-0 medians: 3.3464 kg, 49.8842 cm, 34.4618 cm head circumference.
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2026-01-01"), "MALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.valueOf(3.3464d),
                        BigDecimal.valueOf(49.8842d),
                        null,
                        BigDecimal.valueOf(34.4618d),
                        null,
                        "LENGTH"));

        assertEquals(0, assessment.ageDays());
        assertEquals(GrowthEngine.STANDARD_ID, assessment.standard());
        assertEquals(GrowthEngine.ENGINE_VERSION, assessment.engineVersion());
        assertEquals(new BigDecimal("0.00"), assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE));
        assertEquals(new BigDecimal("0.00"), assessment.zScore(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE));
        assertEquals(new BigDecimal("0.00"), assessment.zScore(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE));
        assertEquals(new BigDecimal("50.0"), assessment.score(GrowthIndicator.WEIGHT_FOR_AGE).percentile());
        assertNull(assessment.unsupportedReason());
    }

    @Test
    void underweightChildScoresBelowMinusTwo() {
        // 12-month-old boy weighing 7.0 kg: well below the 9.6 kg median.
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-07-26"), "MALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-07-26T00:00:00Z"),
                        BigDecimal.valueOf(7.0d),
                        BigDecimal.valueOf(71.0d),
                        null,
                        null,
                        null,
                        "LENGTH"));

        BigDecimal waz = assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE);
        assertNotNull(waz);
        assertTrue(waz.compareTo(BigDecimal.valueOf(-2)) < 0, "expected underweight, got " + waz);
        assertTrue(waz.compareTo(BigDecimal.valueOf(-4)) > 0, "z-score implausibly low: " + waz);
    }

    @Test
    void childOverFiveIsNotScoredAndTheReasonIsStated() {
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2018-01-01"), "FEMALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-01-02T00:00:00Z"),
                        BigDecimal.valueOf(20.0d),
                        null,
                        BigDecimal.valueOf(110.0d),
                        null,
                        null,
                        "HEIGHT"));

        assertNull(assessment.standard());
        assertTrue(assessment.scores().isEmpty());
        assertTrue(assessment.unsupportedReason().contains("5-19"),
                "expected an explanation naming the missing reference: " + assessment.unsupportedReason());
    }

    @Test
    void unrecordedSexBlocksScoringBecauseStandardsAreSexSpecific() {
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2026-01-01"), null),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-02-01T00:00:00Z"),
                        BigDecimal.valueOf(4.2d), null, null, null, null, null));

        assertNull(assessment.standard());
        assertTrue(assessment.unsupportedReason().contains("Sex"));
    }

    @Test
    void pretermInfantIsScoredAgainstCorrectedAge() {
        // Born at 32 weeks, measured at 120 days chronological => 85 days corrected.
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2026-01-01"), "FEMALE", 32),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                        BigDecimal.valueOf(5.0d), null, null, null, null, null));

        assertEquals(120, assessment.ageDays());
        assertEquals(85, assessment.correctedAgeDays());
        assertTrue(assessment.correctedAgeApplied());

        // At 32 weeks' gestation plus 17 completed weeks lived, this infant is 49 weeks
        // postmenstrual age and belongs on the preterm chart, not the term one.
        assertEquals(49, assessment.postmenstrualAgeWeeks());

        // The invariant holds whether or not the preterm reference is loaded in this
        // deployment: either it scored on the preterm chart, or it scored on nothing and
        // said why. What it must never do is quietly answer with the term standard.
        assertNotEquals(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS.standardId(), assessment.standard(),
                "a preterm infant inside the preterm chart's range must never be scored on WHO term standards");
        if (engine.reference(GrowthStandard.FENTON_2013_PRETERM_GROWTH).available()) {
            assertEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), assessment.standard());
            assertNotNull(assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE));
        } else {
            assertNull(assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE));
            assertTrue(assessment.unsupportedReason().contains("not substituted"),
                    "an unscored preterm measurement must say that the term standard was withheld deliberately");
        }
    }

    @Test
    void pretermInfantPastThePretermChartMovesToWhoAtCorrectedAge() {
        // Born at 32 weeks, measured at 300 days chronological => 74 weeks postmenstrual,
        // beyond the Fenton chart, so follow-up continues on WHO at 265 days corrected.
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-01-01"), "FEMALE", 32),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2025-10-28T00:00:00Z"),
                        BigDecimal.valueOf(7.0d), null, null, null, null, null));

        assertEquals(300, assessment.ageDays());
        assertEquals(265, assessment.correctedAgeDays());
        assertEquals(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS.standardId(), assessment.standard());

        // The same weight scored against chronological age would look worse than it is.
        GrowthEngine.GrowthAssessment asIfTerm = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-01-01"), "FEMALE", 40),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2025-10-28T00:00:00Z"),
                        BigDecimal.valueOf(7.0d), null, null, null, null, null));

        assertTrue(assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE)
                        .compareTo(asIfTerm.zScore(GrowthIndicator.WEIGHT_FOR_AGE)) > 0,
                "correcting for prematurity should improve the z-score");
    }

    @Test
    void standingHeightUnderTwoYearsIsAdjustedToLength() {
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-07-26"), "MALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-07-26T00:00:00Z"),
                        BigDecimal.valueOf(9.6d),
                        null,
                        BigDecimal.valueOf(75.0d),
                        null,
                        null,
                        "HEIGHT"));

        assertEquals(new BigDecimal("0.7"), assessment.statureAdjustmentCm());
        assertEquals(0, assessment.normalisedStatureCm().compareTo(BigDecimal.valueOf(75.7d)));
        assertEquals("LENGTH", assessment.normalisedStatureMode());
    }

    @Test
    void recumbentLengthOverTwoYearsIsAdjustedToHeight() {
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2023-07-26"), "FEMALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-07-26T00:00:00Z"),
                        BigDecimal.valueOf(13.9d),
                        BigDecimal.valueOf(95.0d),
                        null,
                        null,
                        null,
                        "LENGTH"));

        assertEquals(new BigDecimal("-0.7"), assessment.statureAdjustmentCm());
        assertEquals(0, assessment.normalisedStatureCm().compareTo(BigDecimal.valueOf(94.3d)));
        assertEquals("HEIGHT", assessment.normalisedStatureMode());
    }

    @Test
    void bmiIsDerivedFromWeightAndStatureWhenNotSupplied() {
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-07-26"), "MALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-07-26T00:00:00Z"),
                        BigDecimal.valueOf(9.6d),
                        BigDecimal.valueOf(75.7d),
                        null,
                        null,
                        null,
                        "LENGTH"));

        // 9.6 / 0.757^2 = 16.75
        assertEquals(0, assessment.bmi().setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(16.75d)));
        assertNotNull(assessment.zScore(GrowthIndicator.BODY_MASS_INDEX_FOR_AGE));
    }

    @Test
    void referenceCurvesRoundTripThroughTheZScoreCalculation() {
        BigDecimal minusTwo = engine.valueAtZScore(GrowthIndicator.WEIGHT_FOR_AGE, "male", 365, -2.0d);
        assertNotNull(minusTwo);

        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2025-07-26"), "MALE"),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-07-26T00:00:00Z"),
                        minusTwo, null, null, null, null, null));

        assertEquals(new BigDecimal("-2.00"), assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE));
    }

    @Test
    void curveLookupReturnsNothingBeyondTheStandardsRange() {
        assertNull(engine.valueAtZScore(GrowthIndicator.WEIGHT_FOR_AGE, "male",
                GrowthEngine.UNDER_FIVE_MAX_AGE_DAYS + 1, 0.0d));
        assertTrue(engine.table(GrowthIndicator.WEIGHT_FOR_AGE, "unknown-sex").isEmpty());
    }

    @Test
    void everyIndicatorHasCompleteDailyTablesForBothSexes() {
        for (GrowthIndicator indicator : GrowthIndicator.values()) {
            for (String sex : new String[]{"male", "female"}) {
                assertEquals(GrowthEngine.UNDER_FIVE_MAX_AGE_DAYS + 1, engine.table(indicator, sex).size(),
                        indicator + "/" + sex + " should have one row per day of the under-five period");
            }
        }
    }
}
