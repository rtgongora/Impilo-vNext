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

/**
 * The Fenton 2013 preterm chart, checked against the publishers' own calculator.
 *
 * <p>The z-scores asserted here are not this engine's output recorded as truth. They are
 * the worked examples shipped inside the University of Calgary bulk calculator spreadsheet
 * that the LMS tables were transcribed from, so a change to the arithmetic, the tables or
 * the unit handling fails against the authors' answer rather than against our own.</p>
 */
class FentonPretermStandardTest {

    private static final double TOLERANCE = 0.005d;

    private final GrowthEngine engine = new GrowthEngine();

    /**
     * Builds a patient and measurement that land on an exact postmenstrual week. Born at
     * {@code gestationalAgeWeeks}, measured after a whole number of weeks lived.
     */
    private GrowthEngine.GrowthAssessment assessAt(int postmenstrualWeeks,
                                                   int gestationalAgeWeeks,
                                                   String sex,
                                                   BigDecimal weightKg,
                                                   BigDecimal lengthCm,
                                                   BigDecimal headCircumferenceCm) {
        int weeksLived = postmenstrualWeeks - gestationalAgeWeeks;
        LocalDate dateOfBirth = LocalDate.parse("2026-01-01");
        OffsetDateTime measuredAt = OffsetDateTime.parse("2026-01-01T00:00:00Z").plusWeeks(weeksLived);
        return engine.assess(
                new GrowthEngine.PatientContext(dateOfBirth, sex, gestationalAgeWeeks),
                new GrowthEngine.GrowthMeasurement(measuredAt, weightKg, lengthCm, null,
                        headCircumferenceCm, null, null));
    }

    private void assertZ(GrowthEngine.GrowthAssessment assessment, GrowthIndicator indicator, double expected) {
        BigDecimal actual = assessment.zScore(indicator);
        assertNotNull(actual, () -> "expected a score for " + indicator);
        assertTrue(Math.abs(actual.doubleValue() - expected) <= TOLERANCE,
                () -> indicator + ": expected " + expected + " from the publishers' calculator, got " + actual);
    }

    @Test
    void reproducesThePublishersWorkedExampleForAGirlAtTwentyFourWeeks() {
        // Girls sheet, sample row 2: 24 completed weeks, 451 g, 27.3 cm, 21.8 cm.
        GrowthEngine.GrowthAssessment assessment = assessAt(24, 24, "FEMALE",
                new BigDecimal("0.451"), new BigDecimal("27.3"), new BigDecimal("21.8"));

        assertEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), assessment.standard());
        assertZ(assessment, GrowthIndicator.WEIGHT_FOR_AGE, -1.8824411984780314d);
        assertZ(assessment, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, -1.8745350202000175d);
        assertZ(assessment, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, -0.0041778403803880235d);
    }

    @Test
    void reproducesThePublishersWorkedExampleForABoyAtThirtySixWeeks() {
        // Boys sheet, sample row 3: 36 completed weeks, 2838 g, 47.7 cm, 33.0 cm — a boy
        // sitting exactly on the median weight.
        GrowthEngine.GrowthAssessment assessment = assessAt(36, 28, "MALE",
                new BigDecimal("2.838"), new BigDecimal("47.7"), new BigDecimal("33.0"));

        assertZ(assessment, GrowthIndicator.WEIGHT_FOR_AGE, 0.0d);
        assertZ(assessment, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, -0.013837867311677524d);
        assertZ(assessment, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, -0.00044178361849332124d);
    }

    @Test
    void reproducesThePublishersWorkedExampleForAGirlAtThirtySixWeeks() {
        // Girls sheet, sample row 4: 36 completed weeks, 1952 g, 42.3 cm, 29.9 cm.
        GrowthEngine.GrowthAssessment assessment = assessAt(36, 30, "FEMALE",
                new BigDecimal("1.952"), new BigDecimal("42.3"), new BigDecimal("29.9"));

        assertZ(assessment, GrowthIndicator.WEIGHT_FOR_AGE, -1.87946258874779d);
        assertZ(assessment, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, -1.8652302820583724d);
        assertZ(assessment, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, -1.8537297506311012d);
    }

    @Test
    void appliesTheExtremeTailCorrectionToWeightExactlyAsThePublishersDo() {
        // Girls sheet, sample row 3: 49 completed weeks, 2720 g. The uncorrected Box-Cox
        // z is about -5.01; the published answer is -4.674 because beyond three standard
        // deviations the score is measured in units of the 2-to-3 SD distance instead.
        GrowthEngine.GrowthAssessment girl = assessAt(49, 30, "FEMALE",
                new BigDecimal("2.720"), new BigDecimal("47.0"), new BigDecimal("32.6"));

        assertZ(girl, GrowthIndicator.WEIGHT_FOR_AGE, -4.6737302841432546d);
        // Length and head circumference are normally distributed on these charts, so they
        // take no correction — asserting them here is what stops the correction leaking.
        assertZ(girl, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, -5.1991511376490802d);
        assertZ(girl, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, -4.7738229157821452d);

        // Boys sheet, sample row 4: 49 completed weeks, 2036 g, 43.2 cm, 30.2 cm.
        GrowthEngine.GrowthAssessment boy = assessAt(49, 27, "MALE",
                new BigDecimal("2.036"), new BigDecimal("43.2"), new BigDecimal("30.2"));

        assertZ(boy, GrowthIndicator.WEIGHT_FOR_AGE, -6.5186718100353112d);
        assertZ(boy, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, -7.7619676271257019d);
        assertZ(boy, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, -7.4844139498659885d);
    }

    @Test
    void reproducesThePublishersWorkedExamplesAtTwentyTwoWeeksWhereOnlyWeightIsPublished() {
        // Girls and boys sample row 1: 22 completed weeks. The source states plainly that
        // length and head circumference have no data at 22 weeks, so they must come back
        // as a named absence rather than a number.
        GrowthEngine.GrowthAssessment girl = assessAt(22, 22, "FEMALE",
                new BigDecimal("0.649"), new BigDecimal("30.9"), new BigDecimal("19.5"));
        assertZ(girl, GrowthIndicator.WEIGHT_FOR_AGE, 1.7380349004361799d);
        assertNull(girl.zScore(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE));
        assertTrue(girl.unavailableIndicators().get(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE)
                        .contains("22 weeks postmenstrual age"),
                "an unpublished point must be reported as a gap naming where it is missing");
        assertNull(girl.zScore(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE));

        GrowthEngine.GrowthAssessment boy = assessAt(22, 22, "MALE",
                new BigDecimal("0.695"), null, null);
        assertZ(boy, GrowthIndicator.WEIGHT_FOR_AGE, 2.1196653115290345d);
    }

    @Test
    void aVeryPretermInfantIsNotDeclaredMalnourishedByTheTermStandard() {
        // The defect this standard exists to close. A 28-week baby of 1.05 kg at two weeks
        // old is growing normally. Corrected age is negative and was clamped to zero, so
        // the WHO term curve for a newborn scored this baby below -6 and the nutrition
        // classifier would have referred a well infant for severe acute malnutrition.
        GrowthEngine.GrowthAssessment assessment = engine.assess(
                new GrowthEngine.PatientContext(LocalDate.parse("2026-01-01"), "MALE", 28),
                new GrowthEngine.GrowthMeasurement(
                        OffsetDateTime.parse("2026-01-15T00:00:00Z"),
                        new BigDecimal("1.05"), null, null, null, null, null));

        assertEquals(30, assessment.postmenstrualAgeWeeks());
        assertEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), assessment.standard());

        BigDecimal z = assessment.zScore(GrowthIndicator.WEIGHT_FOR_AGE);
        assertNotNull(z);
        assertTrue(z.doubleValue() > -2.0d,
                "a normally grown 28-week infant must not read as severely underweight; got " + z);
    }

    @Test
    void theChartIsHandedOverToWhoAtItsPublishedEndAndNotBefore() {
        // Born at 30 weeks: 49 weeks postmenstrual is the last published week, 50 is the
        // first week on WHO at corrected age. Neither is left unscored.
        GrowthEngine.GrowthAssessment lastPretermWeek = assessAt(49, 30, "FEMALE",
                new BigDecimal("3.4"), null, null);
        assertEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), lastPretermWeek.standard());

        GrowthEngine.GrowthAssessment firstTermWeek = assessAt(50, 30, "FEMALE",
                new BigDecimal("3.5"), null, null);
        assertEquals(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS.standardId(), firstTermWeek.standard());
        assertNotNull(firstTermWeek.zScore(GrowthIndicator.WEIGHT_FOR_AGE),
                "the handover must not leave a week where nothing can be scored");

        // THE HANDOVER IS CONTINUOUS IN AGE, which is the property this chart is drawn for.
        // At 50 weeks postmenstrual this infant is 10 weeks past term, so corrected age is
        // 70 days — and 70 is what the WHO tables are now read at. This assertion previously
        // stood at 91, because CorrectedAge reused the preterm threshold (37 weeks) as the
        // correction target instead of full term (40), reading every preterm child about
        // three weeks "old" from the moment they left the preterm chart. It was pinned at
        // the wrong value deliberately so that separating the two constants would trip it.
        // It has now been separated, so this is the conventional value.
        assertEquals(70, firstTermWeek.correctedAgeDays(),
                "corrected age must target full term (40 weeks), so 50 weeks postmenstrual"
                        + " reads as 10 weeks corrected and the Fenton→WHO handover does not"
                        + " jump the child forward in age");
    }

    @Test
    void aTermBabyIsNeverReadOnThePretermChart() {
        GrowthEngine.GrowthAssessment assessment = assessAt(40, 39, "MALE",
                new BigDecimal("3.4"), null, null);

        assertEquals(GrowthStandard.WHO_2006_CHILD_GROWTH_STANDARDS.standardId(), assessment.standard());
        assertNotEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), assessment.standard());
    }

    @Test
    void bmiIsReportedAsUnavailableRatherThanScoredOnAChartThatHasNoBmiTable() {
        GrowthEngine.GrowthAssessment assessment = assessAt(34, 30, "MALE",
                new BigDecimal("2.0"), new BigDecimal("44.0"), new BigDecimal("31.0"));

        assertEquals(GrowthStandard.FENTON_2013_PRETERM_GROWTH.standardId(), assessment.standard());
        assertNull(assessment.zScore(GrowthIndicator.BODY_MASS_INDEX_FOR_AGE));
        assertTrue(assessment.unavailableIndicators().get(GrowthIndicator.BODY_MASS_INDEX_FOR_AGE)
                .contains("does not publish"));
    }

    @Test
    void theReferenceCarriesTheAttributionItsLicenceRequires() {
        GrowthReferenceTables tables = engine.reference(GrowthStandard.FENTON_2013_PRETERM_GROWTH);

        assertTrue(tables.available(), "the preterm reference should be loaded in this build");
        assertEquals("ENGINEERING_SEED", tables.approvalStatus());
        assertEquals("Fenton 2013 Preterm Growth Chart",
                GrowthStandard.FENTON_2013_PRETERM_GROWTH.requiredChartLabel());
        assertTrue(GrowthStandard.FENTON_2013_PRETERM_GROWTH.citation().contains("BMC Pediatr. 2013;13:59"));
        assertEquals("CC BY-NC-ND 4.0", GrowthStandard.FENTON_2013_PRETERM_GROWTH.licence());
    }

    @Test
    void curvePointsAreReturnedInTheUnitTheMeasurementArrivesIn() {
        // The tables publish weight in grams; a curve drawn for a screen that records
        // kilograms must not be off by a factor of a thousand.
        BigDecimal median = engine.valueAtZScore(GrowthStandard.FENTON_2013_PRETERM_GROWTH,
                GrowthIndicator.WEIGHT_FOR_AGE, "male", 36, 0.0d);

        assertNotNull(median);
        assertTrue(median.doubleValue() > 2.0d && median.doubleValue() < 3.5d,
                "median weight at 36 weeks should be a few kilograms, got " + median);
    }
}
