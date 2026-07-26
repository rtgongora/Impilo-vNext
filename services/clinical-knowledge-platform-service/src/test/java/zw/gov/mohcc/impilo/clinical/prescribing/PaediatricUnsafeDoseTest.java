package zw.gov.mohcc.impilo.clinical.prescribing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceDocumentRepository;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paediatric overdose is usually a decimal-point slip that looks entirely ordinary written
 * down. It has to stop the prescription rather than sit beside it as a warning.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaediatricUnsafeDoseTest {

    @Mock private MedicineGuidanceRepository medicineGuidanceRepository;
    @Mock private TraceService traceService;
    @Mock private SourceDocumentRepository sourceDocumentRepository;

    private PrescribingEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new PrescribingEvaluationService(
                new ClinicalRulesEngine(3), medicineGuidanceRepository, traceService, sourceDocumentRepository);
        ReflectionTestUtils.setField(service, "doseCalculationService",
                new DoseCalculationService(new ObjectMapper()));
        org.mockito.Mockito.when(medicineGuidanceRepository.search(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());
    }

    @Test
    void aTenfoldOverdoseInAChildIsACriticalNonOverridableAlert() {
        // 3350 mg where 335 mg was calculated: the classic misplaced decimal point.
        Map<String, Object> result = service.evaluate(request(425, 8.4, "amoxicillin", 3350.0));

        var alerts = alerts(result);
        var unsafe = alerts.stream()
                .filter(a -> "UNSAFE_DOSE_PAEDIATRIC".equals(a.get("code")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a tenfold overdose must be flagged"));

        assertThat(unsafe.get("severity")).isEqualTo("CRITICAL");
        assertThat(unsafe.get("interruptive")).isEqualTo(true);
        assertThat(unsafe.get("override_allowed")).isEqualTo(false);
        assertThat(String.valueOf(unsafe.get("message"))).contains("decimal point");
    }

    @Test
    void aCorrectDoseRaisesNoUnsafeDoseAlert() {
        Map<String, Object> result = service.evaluate(request(425, 8.4, "amoxicillin", 335.0));

        assertThat(codes(result)).doesNotContain("UNSAFE_DOSE_PAEDIATRIC");
    }

    @Test
    void aSmallRoundingDifferenceIsToleratedRatherThanAlarming() {
        // Prescribing 350 mg where 335 was calculated is ordinary clinical rounding.
        Map<String, Object> result = service.evaluate(request(425, 8.4, "amoxicillin", 350.0));

        assertThat(codes(result)).doesNotContain("UNSAFE_DOSE_PAEDIATRIC");
    }

    @Test
    void theSuggestedPaediatricDoseIsAttachedToTheTherapyLine() {
        Map<String, Object> result = service.evaluate(request(425, 8.4, "amoxicillin", null));

        Map<?, ?> line = (Map<?, ?>) ((List<?>) result.get("therapy_evaluation")).get(0);
        Map<?, ?> dose = (Map<?, ?>) line.get("paediatric_dose");

        assertThat(dose.get("calculated")).isEqualTo(true);
        assertThat(dose.get("dose_mg")).isEqualTo(335.0);
        assertThat(dose.get("requires_authorisation")).isEqualTo(true);
    }

    @Test
    void anAdultPrescriptionIsNotGivenAPaediatricDoseBlock() {
        Map<String, Object> result = service.evaluate(request(40 * 365, 70.0, "amoxicillin", 1000.0));

        Map<?, ?> line = (Map<?, ?>) ((List<?>) result.get("therapy_evaluation")).get(0);
        assertThat(line.containsKey("paediatric_dose")).isFalse();
        assertThat(codes(result)).doesNotContain("UNSAFE_DOSE_PAEDIATRIC");
    }

    @Test
    void withoutAWeightNoDoseIsSuggestedAndNoOverdoseIsClaimed() {
        // The absence of a weight must not be read as the absence of a problem, nor as one.
        Map<String, Object> result = service.evaluate(request(425, null, "amoxicillin", 3350.0));

        Map<?, ?> line = (Map<?, ?>) ((List<?>) result.get("therapy_evaluation")).get(0);
        Map<?, ?> dose = (Map<?, ?>) line.get("paediatric_dose");

        assertThat(dose.get("calculated")).isEqualTo(false);
        assertThat(dose.get("reason")).isEqualTo("NO_VERIFIED_WEIGHT");
        assertThat(codes(result)).doesNotContain("UNSAFE_DOSE_PAEDIATRIC");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> alerts(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("alerts");
    }

    private static List<String> codes(Map<String, Object> result) {
        return alerts(result).stream().map(a -> String.valueOf(a.get("code"))).toList();
    }

    private static Map<String, Object> request(Integer ageDays, Double weightKg, String medicine, Double doseMg) {
        Map<String, Object> medication = new LinkedHashMap<>();
        medication.put("genericName", medicine);
        if (doseMg != null) {
            medication.put("doseMg", doseMg);
        }
        medication.put("route", "ORAL");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ageDays", ageDays);
        if (weightKg != null) {
            body.put("weightKg", weightKg);
        }
        body.put("indication", "PNEUMONIA");
        body.put("proposedMedications", List.of(medication));
        return body;
    }
}
