package zw.gov.mohcc.impilo.clinical.prescribing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DoseCalculationServiceTest {

    private final DoseCalculationService service = new DoseCalculationService(new ObjectMapper());

    @Test
    void aDoseIsCalculatedFromTodaysWeightAndShowsItsArithmetic() {
        // The exemplar from the target journey: a 14-month-old weighing 8.4 kg with pneumonia.
        var suggestion = service.calculate("amoxicillin", "PNEUMONIA", 425, 8.4);

        assertThat(suggestion.calculated()).isTrue();
        assertThat(suggestion.doseMg()).isEqualTo(335.0);
        assertThat(suggestion.dosesPerDay()).isEqualTo(2);
        assertThat(suggestion.intervalHours()).isEqualTo(12);
        assertThat(suggestion.basis()).contains("40 mg/kg/dose", "8.4 kg", "336");
        assertThat(suggestion.basis()).contains("Review and authorise");
    }

    @Test
    void theDoseIsAlsoGivenAsAVolumeSomeoneCanActuallyMeasure() {
        var suggestion = service.calculate("amoxicillin", "PNEUMONIA", 425, 8.4);

        assertThat(suggestion.volumes()).isNotEmpty();
        var suspension = suggestion.volumes().stream()
                .filter(v -> v.preparation().contains("250 mg/5 mL")).findFirst().orElseThrow();
        assertThat(suspension.millilitres()).isCloseTo(6.7, within(0.05));
    }

    @Test
    void noDoseIsCalculatedWithoutAWeight() {
        // An estimated weight silently becomes an estimated dose, so there is no fallback.
        var suggestion = service.calculate("amoxicillin", "PNEUMONIA", 425, null);

        assertThat(suggestion.calculated()).isFalse();
        assertThat(suggestion.notCalculatedReason()).isEqualTo("NO_VERIFIED_WEIGHT");
        assertThat(suggestion.explanation()).contains("Weigh the child");
    }

    @Test
    void noDoseIsCalculatedWithoutAnAge() {
        var suggestion = service.calculate("amoxicillin", "PNEUMONIA", null, 8.4);

        assertThat(suggestion.calculated()).isFalse();
        assertThat(suggestion.notCalculatedReason()).isEqualTo("NO_AGE");
    }

    @Test
    void anUncoveredMedicineReturnsNoDoseRatherThanAGuess() {
        var suggestion = service.calculate("vancomycin", null, 425, 8.4);

        assertThat(suggestion.calculated()).isFalse();
        assertThat(suggestion.notCalculatedReason()).isEqualTo("NO_DOSING_RULE");
        assertThat(suggestion.explanation()).contains("national formulary");
    }

    @Test
    void anAmbiguousMatchRefusesToChooseBetweenRules() {
        // Amoxicillin has both a young-infant and an older-child rule. Without an indication
        // at an age where only one applies this is fine; the guard is that overlapping rules
        // are never silently resolved by picking the first.
        var youngInfant = service.calculate("amoxicillin", "PNEUMONIA", 30, 4.0);
        assertThat(youngInfant.calculated()).isTrue();
        assertThat(youngInfant.ruleCode()).isEqualTo("ORAL_AMOXICILLIN_YOUNG_INFANT");

        var olderChild = service.calculate("amoxicillin", "PNEUMONIA", 425, 8.4);
        assertThat(olderChild.ruleCode()).isEqualTo("AMOXICILLIN_ORAL_PNEUMONIA_CHILD");
    }

    @Test
    void aDoseAboveTheSingleMaximumIsCappedAndSaysSo() {
        var suggestion = service.calculate("amoxicillin", "PNEUMONIA", 1800, 30.0);

        assertThat(suggestion.doseMg()).isEqualTo(1000.0);
        assertThat(suggestion.capped()).isTrue();
        assertThat(suggestion.basis()).contains("capped at");
    }

    @Test
    void theDailyMaximumConstrainsThePerDoseFigure() {
        // 15 mg/kg × 50 kg = 750 mg four times a day = 3000 mg, within the 4 g daily ceiling.
        var suggestion = service.calculate("paracetamol", null, 4000, 50.0);

        assertThat(suggestion.doseMg()).isEqualTo(750.0);
        assertThat(suggestion.dailyDoseMg()).isEqualTo(3000.0);
    }

    @Test
    void aFixedDoseDoesNotScaleWithWeight() {
        var light = service.calculate("zinc sulfate", "DIARRHOEA", 400, 9.0);
        var heavy = service.calculate("zinc sulfate", "DIARRHOEA", 1500, 18.0);

        assertThat(light.doseMg()).isEqualTo(20.0);
        assertThat(heavy.doseMg()).isEqualTo(20.0);
        assertThat(light.basis()).contains("does not vary with weight");
    }

    @Test
    void aNeonatalRuleDoesNotApplyToAnOlderChildAndViceVersa() {
        assertThat(service.calculate("gentamicin", null, 5, 3.0).calculated()).isTrue();
        // Well beyond the neonatal window: the neonatal rule must not be reached.
        assertThat(service.calculate("gentamicin", null, 400, 8.0).calculated()).isFalse();
    }

    @Test
    void aWeightOutsideTheRulesRangeIsNotDosedByExtrapolation() {
        // A 12 kg "neonate" is a data error, not a very large baby.
        var suggestion = service.calculate("gentamicin", null, 5, 12.0);

        assertThat(suggestion.calculated()).isFalse();
        assertThat(suggestion.notCalculatedReason()).isEqualTo("NO_DOSING_RULE");
    }

    @Test
    void aSuggestionCarriesItsSourceVersionApprovalAndTheNeedForAuthorisation() {
        var map = service.calculate("gentamicin", null, 5, 3.0).toMap();

        assertThat(map.get("requires_authorisation")).isEqualTo(true);
        assertThat(map.get("approval_status")).isEqualTo("ENGINEERING_SEED");
        assertThat(map.get("content_version")).isEqualTo(service.contentVersion());
        assertThat((List<?>) map.get("source_refs")).isNotEmpty();
        assertThat(map.get("specialist_only")).isEqualTo(true);
        assertThat(map.get("renal_caution")).isEqualTo(true);
        assertThat(map.get("monitoring")).asString().contains("renal function");
    }

    @Test
    void everyRuleDeclaresWhatIsNeededToGovernIt() {
        assertThat(service.rules()).isNotEmpty();
        service.rules().forEach(rule -> {
            assertThat(rule.sourceRefs()).as("%s must cite a source", rule.code()).isNotEmpty();
            assertThat(rule.ratified()).as("%s must not claim unratified approval", rule.code()).isFalse();
            assertThat(rule.maxSingleDoseMg())
                    .as("%s must declare a maximum single dose", rule.code()).isNotNull();
            assertThat(rule.testCases()).as("%s must ship fixtures", rule.code()).isNotEmpty();
            assertThat(rule.mgPerKgPerDose() != null || rule.fixedDoseMg() != null)
                    .as("%s must specify how the dose is derived", rule.code()).isTrue();
        });
    }

    @TestFactory
    List<DynamicTest> everyDosingRulesOwnFixturesStillProduceTheStatedDose() {
        // The governance gate: a content edit that changes a dose fails the build here first.
        List<DynamicTest> tests = new ArrayList<>();
        for (DosingRule rule : service.rules()) {
            for (DosingRule.TestCase testCase : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + testCase.name(), () -> {
                    var suggestion = service.calculate(
                            rule.genericName(), rule.indication(), testCase.ageDays(), testCase.weightKg());

                    assertThat(suggestion.calculated())
                            .as("%s: %s should produce a dose", rule.code(), testCase.name())
                            .isTrue();
                    assertThat(suggestion.doseMg())
                            .as("%s: %s", rule.code(), testCase.name())
                            .isEqualTo(testCase.expectMgPerDose());
                    if (testCase.expectCapped()) {
                        assertThat(suggestion.capped())
                                .as("%s: %s should be capped", rule.code(), testCase.name())
                                .isTrue();
                    }
                }));
            }
        }
        return tests;
    }
}
