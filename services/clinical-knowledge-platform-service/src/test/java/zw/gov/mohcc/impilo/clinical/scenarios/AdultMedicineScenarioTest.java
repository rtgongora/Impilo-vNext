package zw.gov.mohcc.impilo.clinical.scenarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MedicineClassifier;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContent;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.Condition;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.Investigation;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityEngine;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityView;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityView.DetectionState;
import zw.gov.mohcc.impilo.medicine.pharm.RenalDosingAdvisor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brief §22 — named clinical scenarios encoded against real domain logic (not page snapshots).
 *
 * <p>Each test names a scenario a clinician would recognise, calls the governed engine or calculator
 * that owns the decision, and asserts the outcome the brief expects. These are integration-style
 * proofs that the arithmetic and set reasoning stay aligned with multimorbidity and renal-dosing
 * doctrine.</p>
 */
class AdultMedicineScenarioTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MultimorbidityContent content = new MultimorbidityContent(objectMapper);
    private final MedicineClassifier classifier = new MedicineClassifier(objectMapper);
    private final MultimorbidityEngine engine = new MultimorbidityEngine(content, classifier);

    private static Condition dx(String code, String display) {
        return new Condition(code, display, true);
    }

    private static MultimorbidityView.Detection detection(MultimorbidityView view, String key) {
        return view.detections().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("§22 Geriatrics — frailty plus diabetes raises glycaemic target conflict")
    void geriatricsFrailtyAndDiabetesGlycaemicConflictIsDetected() {
        MultimorbidityView view = engine.assess(
                new MultimorbidityContext(
                        List.of(dx("E11", "Type 2 diabetes"), dx("R54", "Frailty")),
                        List.of(), List.of(), List.of(), null, null, null, null, null),
                TODAY);

        MultimorbidityView.Detection d = detection(view, "contradictory_targets");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings()).extracting(MultimorbidityView.Finding::code)
                .contains("MM_GLYCAEMIC_TARGET_VS_FRAILTY");
        assertThat(d.findings().get(0).requiredAction()).contains("relaxed");
    }

    @Test
    @DisplayName("§22 Diabetes — duplicate HbA1c inside biological interval is detected")
    void diabetesDuplicateHba1cInsideIntervalIsDetected() {
        List<Investigation> invs = List.of(
                new Investigation("HBA1C", TODAY.minusDays(30), "Diabetes clinic"),
                new Investigation("HBA1C", TODAY.minusDays(10), "Medical outpatients"));

        MultimorbidityView.Detection d = detection(
                engine.assess(
                        new MultimorbidityContext(
                                List.of(dx("E11", "Type 2 diabetes")),
                                List.of(), List.of(), invs, null, null, null, null, null),
                        TODAY),
                "repeated_investigations");

        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings().get(0).message()).containsIgnoringCase("HbA1c");
    }

    @Test
    @DisplayName("§22 Multimorbidity — CKD and diabetes diet conflict is detected")
    void multimorbidityCkdAndDiabetesDietConflictIsDetected() {
        MultimorbidityView view = engine.assess(
                new MultimorbidityContext(
                        List.of(dx("N18.3", "Chronic kidney disease stage 3"), dx("E11", "Type 2 diabetes")),
                        List.of(), List.of(), List.of(), null, null, null, null, null),
                TODAY);

        MultimorbidityView.Detection d = detection(view, "competing_dietary_advice");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings()).extracting(MultimorbidityView.Finding::code)
                .contains("MM_DIET_CKD_VS_DIABETES");
    }

    @Test
    @DisplayName("§22 Renal dosing — metformin at eGFR 25 is contraindicated not cautioned")
    void renalDosingMetforminAtEgfrTwentyFiveIsAdjustNotInvented() {
        RenalDosingAdvisor.Advice advice = RenalDosingAdvisor.advise(25.0, "BIGUANIDE");

        assertThat(advice.computed()).isTrue();
        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.ADJUST);
        assertThat(advice.egfr()).isEqualTo(25.0);
        assertThat(advice.message()).contains("contraindicated").contains("30");
    }

    @Test
    @DisplayName("§22 Renal dosing — missing eGFR never invents metformin advice")
    void renalDosingWithoutEgfrStaysUnknown() {
        RenalDosingAdvisor.Advice advice = RenalDosingAdvisor.advise(null, "BIGUANIDE");

        assertThat(advice.computed()).isFalse();
        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.UNKNOWN);
        assertThat(advice.message()).contains("eGFR is not available");
    }
}
