package zw.gov.mohcc.impilo.paediatrics.growth;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionClassifierTest {

    private static final int EIGHTEEN_MONTHS = 548;

    @Test
    void muacBelowElevenPointFiveIsSevereAcuteMalnutrition() {
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, BigDecimal.valueOf(10.9d),
                NutritionClassifier.Oedema.ABSENT, BigDecimal.valueOf(-2.4d));

        assertEquals(NutritionStatus.SEVERE_ACUTE_MALNUTRITION, assessment.status());
        assertTrue(assessment.imamReferralIndicated());
        assertTrue(assessment.findings().stream().anyMatch(f -> f.contains("11.5")));
    }

    @Test
    void muacBetweenCutOffsIsModerateAcuteMalnutrition() {
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, BigDecimal.valueOf(12.0d),
                NutritionClassifier.Oedema.ABSENT, BigDecimal.valueOf(-1.5d));

        assertEquals(NutritionStatus.MODERATE_ACUTE_MALNUTRITION, assessment.status());
        assertTrue(assessment.imamReferralIndicated());
    }

    @Test
    void oedemaIsSevereEvenWhenEveryMeasurementLooksNormal() {
        // A child with nutritional oedema can weigh well: the fluid masks the wasting.
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, BigDecimal.valueOf(14.0d),
                NutritionClassifier.Oedema.PRESENT, BigDecimal.valueOf(0.2d));

        assertEquals(NutritionStatus.SEVERE_ACUTE_MALNUTRITION, assessment.status());
        assertTrue(assessment.imamReferralIndicated());
        assertTrue(assessment.findings().stream().anyMatch(f -> f.contains("oedema")));
    }

    @Test
    void adequateMeasurementsDoNotTriggerAReferral() {
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, BigDecimal.valueOf(14.5d),
                NutritionClassifier.Oedema.ABSENT, BigDecimal.valueOf(0.1d));

        assertEquals(NutritionStatus.ADEQUATE, assessment.status());
        assertFalse(assessment.imamReferralIndicated());
    }

    @Test
    void nothingMeasuredIsNotAssessedRatherThanAdequate() {
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, null,
                NutritionClassifier.Oedema.NOT_ASSESSED, null);

        assertEquals(NutritionStatus.NOT_ASSESSED, assessment.status());
        assertFalse(assessment.imamReferralIndicated());
        assertTrue(assessment.findings().get(0).contains("unknown"));
    }

    @Test
    void muacIsNotInterpretedOutsideSixToFiftyNineMonths() {
        var newborn = NutritionClassifier.classify(20, BigDecimal.valueOf(9.0d),
                NutritionClassifier.Oedema.ABSENT, null);

        assertEquals(NutritionStatus.NOT_ASSESSED, newborn.status());
        assertTrue(newborn.assessmentGaps().stream().anyMatch(g -> g.contains("6 and 59 months")));
        assertFalse(NutritionClassifier.muacInterpretable(182));
        assertTrue(NutritionClassifier.muacInterpretable(183));
        assertFalse(NutritionClassifier.muacInterpretable(1825));
    }

    @Test
    void everyAssessmentDeclaresTheMissingWastingIndicator() {
        var assessment = NutritionClassifier.classify(EIGHTEEN_MONTHS, BigDecimal.valueOf(13.0d),
                NutritionClassifier.Oedema.ABSENT, BigDecimal.valueOf(-0.5d));

        assertTrue(assessment.assessmentGaps().stream().anyMatch(g -> g.contains("Weight-for-height")));
        assertEquals(NutritionClassifier.CONTENT_VERSION, assessment.contentVersion());
    }

    @Test
    void severityOrderingPicksTheWorstFinding() {
        assertTrue(NutritionStatus.SEVERE_ACUTE_MALNUTRITION
                .atLeastAsSevereAs(NutritionStatus.MODERATE_ACUTE_MALNUTRITION));
        assertFalse(NutritionStatus.ADEQUATE.atLeastAsSevereAs(NutritionStatus.AT_RISK));
        assertEquals(NutritionStatus.SEVERE_ACUTE_MALNUTRITION,
                NutritionStatus.AT_RISK.mostSevere(NutritionStatus.SEVERE_ACUTE_MALNUTRITION));
    }
}
