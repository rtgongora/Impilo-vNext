package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EwsCalculatorEngineTest {

    private final EwsCalculatorEngine engine = new EwsCalculatorEngine(new ObjectMapper());

    private static final int THREE_MONTHS = 90;
    private static final int TEN_YEARS = 3650;
    private static final int THIRTY_YEARS = 30 * 365;

    @Test
    void theSameVitalsScoreDifferentlyForAnInfantAndATenYearOld() {
        // A heart rate of 150 with a respiratory rate of 45 is ordinary in a three-month-old
        // and a serious deterioration in a ten-year-old. One threshold table cannot be right
        // for both, which is the whole reason this engine exists.
        Map<String, Object> vitals = observations(150, 45, 97);

        var infant = engine.calculate(null, THREE_MONTHS, vitals);
        var child = engine.calculate(null, TEN_YEARS, vitals);

        assertThat(infant.scoreType()).isEqualTo(EwsCalculatorEngine.SCORE_TYPE_PEWS);
        assertThat(child.scoreType()).isEqualTo(EwsCalculatorEngine.SCORE_TYPE_PEWS);
        assertThat(infant.ageBandId()).isEqualTo("INFANT_0_11M");
        assertThat(child.ageBandId()).isEqualTo("SCHOOL_AGE_5_11Y");
        assertThat(child.totalScore()).isGreaterThan(infant.totalScore());
        assertThat(infant.totalScore()).isZero();
    }

    @Test
    void aTrulyDeterioratingInfantScoresHigh() {
        Map<String, Object> vitals = observations(195, 75, 88);
        vitals.put("consciousness", "PAIN");
        vitals.put("respiratoryEffort", "SEVERE_RECESSION");
        vitals.put("capillaryRefill", "OVER_4_SECONDS");
        vitals.put("supplementalOxygen", "YES");
        vitals.put("systolicBloodPressure", 55);

        var calculation = engine.calculate(null, THREE_MONTHS, vitals);

        assertThat(calculation.riskLevel()).isEqualTo("HIGH");
        assertThat(calculation.totalScore()).isGreaterThanOrEqualTo(7);
        assertThat(calculation.incomplete()).isFalse();
    }

    @Test
    void adultsAreScoredWithNews2FromThirteenYearsUpwards() {
        assertThat(engine.scoreTypeForAge(THIRTY_YEARS)).isEqualTo(EwsCalculatorEngine.SCORE_TYPE_NEWS2);
        assertThat(engine.scoreTypeForAge(13 * 365)).isEqualTo(EwsCalculatorEngine.SCORE_TYPE_NEWS2);
        assertThat(engine.scoreTypeForAge(13 * 365 - 1)).isEqualTo(EwsCalculatorEngine.SCORE_TYPE_PEWS);
    }

    @Test
    void anAdultWithNormalObservationsScoresZero() {
        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("respiratoryRate", 16);
        vitals.put("oxygenSaturation", 98);
        vitals.put("heartRate", 72);
        vitals.put("systolicBloodPressure", 120);
        vitals.put("temperature", 36.8);
        vitals.put("consciousness", "ALERT");
        vitals.put("supplementalOxygen", "NO");

        var calculation = engine.calculate(EwsCalculatorEngine.SCORE_TYPE_NEWS2, THIRTY_YEARS, vitals);

        assertThat(calculation.totalScore()).isZero();
        assertThat(calculation.riskLevel()).isEqualTo("NONE");
        assertThat(calculation.incomplete()).isFalse();
    }

    @Test
    void anAdultInShockScoresHigh() {
        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("respiratoryRate", 28);
        vitals.put("oxygenSaturation", 90);
        vitals.put("heartRate", 135);
        vitals.put("systolicBloodPressure", 85);
        vitals.put("temperature", 35.0);
        vitals.put("consciousness", "VOICE");
        vitals.put("supplementalOxygen", "YES");

        var calculation = engine.calculate(EwsCalculatorEngine.SCORE_TYPE_NEWS2, THIRTY_YEARS, vitals);

        assertThat(calculation.riskLevel()).isEqualTo("HIGH");
        assertThat(calculation.contributions()).containsKeys(
                "respiratoryRate", "oxygenSaturation", "heartRate", "systolicBloodPressure", "consciousness");
    }

    @Test
    void anUnobservedParameterIsReportedRatherThanScoredAsZero() {
        // A low total built from half the observations reads as reassurance. It must not.
        Map<String, Object> partial = new LinkedHashMap<>();
        partial.put("heartRate", 120);

        var calculation = engine.calculate(null, THREE_MONTHS, partial);

        assertThat(calculation.calculable()).isTrue();
        assertThat(calculation.incomplete()).isTrue();
        assertThat(calculation.missingParameters())
                .contains("respiratoryRate", "oxygenSaturation", "consciousness");
        assertThat(calculation.contributions()).containsOnlyKeys("heartRate");
    }

    @Test
    void withNoObservationsAtAllNothingIsScored() {
        var calculation = engine.calculate(null, THREE_MONTHS, Map.of());

        assertThat(calculation.calculable()).isFalse();
        assertThat(calculation.reason()).contains("No observations");
    }

    @Test
    void withoutAnAgeAPaediatricScoreIsRefusedRatherThanGuessed() {
        // Silently scoring a child against adult thresholds is exactly the failure being fixed.
        var calculation = engine.calculate(EwsCalculatorEngine.SCORE_TYPE_PEWS, null, observations(150, 45, 97));

        assertThat(calculation.calculable()).isFalse();
        assertThat(calculation.reason()).contains("age");
    }

    @Test
    void withNeitherAgeNorScoreTypeNothingIsAssumed() {
        var calculation = engine.calculate(null, null, observations(90, 18, 98));

        assertThat(calculation.calculable()).isFalse();
        assertThat(calculation.reason()).contains("age");
    }

    @Test
    void anUnrecognisedCodedValueIsTreatedAsUnobservedNotAsNormal() {
        Map<String, Object> vitals = observations(120, 30, 97);
        vitals.put("consciousness", "SEEMS_FINE");

        var calculation = engine.calculate(null, THREE_MONTHS, vitals);

        assertThat(calculation.missingParameters()).contains("consciousness");
        assertThat(calculation.contributions()).doesNotContainKey("consciousness");
    }

    @Test
    void everyScoreCarriesTheVersionOfTheThresholdsThatProducedIt() {
        var calculation = engine.calculate(null, THREE_MONTHS, observations(120, 30, 97));

        assertThat(calculation.contentVersion()).isEqualTo(engine.contentVersion());
        assertThat(calculation.contentVersion()).isNotBlank();
    }

    @Test
    void ageBandBoundariesSelectTheIntendedTable() {
        assertThat(engine.calculate(null, 364, observations(120, 30, 97)).ageBandId()).isEqualTo("INFANT_0_11M");
        assertThat(engine.calculate(null, 365, observations(120, 30, 97)).ageBandId()).isEqualTo("YOUNG_CHILD_1_4Y");
        assertThat(engine.calculate(null, 1824, observations(120, 30, 97)).ageBandId()).isEqualTo("YOUNG_CHILD_1_4Y");
        assertThat(engine.calculate(null, 1825, observations(120, 30, 97)).ageBandId()).isEqualTo("SCHOOL_AGE_5_11Y");
    }

    @Test
    void numericValuesArrivingAsStringsAreStillScored() {
        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("heartRate", "195");
        vitals.put("respiratoryRate", "75");
        vitals.put("oxygenSaturation", "88");

        var calculation = engine.calculate(null, THREE_MONTHS, vitals);

        assertThat(calculation.totalScore()).isGreaterThanOrEqualTo(7);
    }

    private static Map<String, Object> observations(int heartRate, int respiratoryRate, int oxygenSaturation) {
        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("heartRate", heartRate);
        vitals.put("respiratoryRate", respiratoryRate);
        vitals.put("oxygenSaturation", oxygenSaturation);
        return vitals;
    }
}
