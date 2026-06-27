package zw.gov.mohcc.impilo.clinical.interpretation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalContextEnricher;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.terminology.UcumUnitConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wiring test for {@link InterpretationEvaluationService}: interpretation → rules → audit, end to end,
 * incl. fail-closed NO_REFERENCE_RANGE and the critical-lab rule firing off an interpreted critical value.
 */
class InterpretationEvaluationServiceTest {

    private final UcumUnitConverter ucum = new UcumUnitConverter();
    private TraceService traceService;

    private static final QualifiedInterval K_INTERVAL = new QualifiedInterval(
            new BigDecimal("3.5"), new BigDecimal("5.1"), "mmol/L",
            new BigDecimal("2.5"), new BigDecimal("6.5"), Sex.UNKNOWN,
            null, null, false, null, null, null, "reference", "art-K", "1.0.0",
            "http://impilo.health.zw/ObservationDefinition/potassium");

    @BeforeEach
    void setUp() {
        traceService = mock(TraceService.class);
        RecommendationTraceEntity stub = new RecommendationTraceEntity();
        stub.setId(UUID.randomUUID());
        when(traceService.record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(stub);
    }

    private InterpretationEvaluationService serviceWith(List<QualifiedInterval> intervals) {
        RangeResolver resolver = new RangeResolver((loinc, local) -> intervals, ucum, false);
        InterpretationEngine engine = new InterpretationEngine(resolver, ucum);
        // ClinicalContextEnricher with a mocked repository (search returns empty by default — no enrichment).
        ClinicalContextEnricher enricher = new ClinicalContextEnricher(mock(MedicineGuidanceRepository.class));
        ClinicalRulesEngine rules = new ClinicalRulesEngine(3);
        return new InterpretationEvaluationService(engine, enricher, rules, traceService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluate_interpretsAndFiresCriticalRule_andRecordsProvenance() {
        var service = serviceWith(List.of(K_INTERVAL));
        Map<String, Object> context = Map.of("ageYears", 40, "sex", "MALE");
        List<Map<String, Object>> observations = List.of(Map.of(
                "loincCode", "2823-3", "displayName", "Potassium", "category", "LAB",
                "value", 7.0, "unit", "mmol/L"));

        Map<String, Object> data = service.evaluate("actor-1", "p-1", "enc-1", context, observations);

        List<Map<String, Object>> interpreted = (List<Map<String, Object>>) data.get("interpreted_observations");
        assertThat(interpreted).hasSize(1);
        assertThat(interpreted.get(0).get("interpretation")).isEqualTo("CRITICAL_HIGH");
        assertThat(interpreted.get(0).get("fhir_interpretation_code")).isEqualTo("HH");

        List<Map<String, Object>> alerts = (List<Map<String, Object>>) data.get("alerts");
        assertThat(alerts.stream().map(a -> a.get("code")))
                .contains("CRITICAL_LAB_VALUE", "HYPERKALAEMIA");

        List<Map<String, Object>> ranges = (List<Map<String, Object>>) data.get("ranges_used");
        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).get("version")).isEqualTo("1.0.0");
        assertThat(ranges.get(0).get("source")).isEqualTo("OBSERVATION_DEFINITION");

        assertThat(data.get("trace_id")).isNotNull();
        assertThat(data.get("advisory")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluate_noReferenceRange_isNeverNormal_andNoFabricatedAlerts() {
        var service = serviceWith(List.of()); // no governed intervals, no lab-delivered
        Map<String, Object> context = Map.of("ageYears", 40, "sex", "MALE");
        List<Map<String, Object>> observations = List.of(Map.of(
                "loincCode", "2823-3", "displayName", "Potassium", "category", "LAB",
                "value", 7.0, "unit", "mmol/L"));

        Map<String, Object> data = service.evaluate("actor-1", "p-1", "enc-1", context, observations);

        List<Map<String, Object>> interpreted = (List<Map<String, Object>>) data.get("interpreted_observations");
        assertThat(interpreted.get(0).get("interpretation")).isEqualTo("NO_REFERENCE_RANGE");
        assertThat(interpreted.get(0).get("interpretation")).isNotEqualTo("NORMAL");

        List<Map<String, Object>> alerts = (List<Map<String, Object>>) data.get("alerts");
        assertThat(alerts.stream().map(a -> a.get("code")))
                .doesNotContain("CRITICAL_LAB_VALUE", "HYPERKALAEMIA");
        assertThat((List<?>) data.get("ranges_used")).isEmpty();
    }
}
