package zw.gov.mohcc.impilo.clinical.cds;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.llm.LlmClinicalClient;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.reasoning.ClinicalPromptBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CdsInsightServiceTest {

    private final LlmClinicalClient client = mock(LlmClinicalClient.class);
    private final TraceService traceService = mock(TraceService.class);

    private CdsInsightService service(boolean enabled) {
        return new CdsInsightService(client, new ClinicalPromptBuilder(), traceService, enabled);
    }

    private List<Map<String, Object>> alerts() {
        return List.of(Map.of("code", "DRUG_ALLERGY_INTERACTION", "severity", "CRITICAL", "message", "x"));
    }

    private void stub(Map<String, Object> out) {
        when(client.requestStructured(anyString(), any(), any(), any(), anyString(), anyDouble()))
                .thenReturn(Optional.of(new LlmClinicalClient.LlmStructuredResult("anthropic", "claude", out, false, "a1")));
        RecommendationTraceEntity trace = new RecommendationTraceEntity();
        trace.setId(UUID.randomUUID());
        when(traceService.record(anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(trace);
    }

    @Test
    void disabledOrNoAlerts_returnsNullInsight() {
        assertThat(service(false).summarise("t", "a", Map.of(), alerts(), null).get("insight")).isNull();
        assertThat(service(true).summarise("t", "a", Map.of(), List.of(), null).get("insight")).isNull();
    }

    @Test
    void codeNotInSuppliedSet_isRejected() {
        stub(Map.of(
                "summary", "Prioritise the renal alert",
                "prioritised_alert_codes", List.of("SOME_CODE_NOT_RAISED"),
                "advisory", "x",
                "references_only_supplied_alerts", false));
        assertThat(service(true).summarise("t", "a", Map.of(), alerts(), null).get("insight")).isNull();
    }

    @Test
    void groundedSummary_returnsAuditedInsight() {
        stub(Map.of(
                "summary", "Critical drug-allergy interaction — review before administering.",
                "prioritised_alert_codes", List.of("DRUG_ALLERGY_INTERACTION"),
                "advisory", "Confirm the allergy and hold the conflicting medication.",
                "references_only_supplied_alerts", true));

        @SuppressWarnings("unchecked")
        Map<String, Object> insight = (Map<String, Object>) service(true)
                .summarise("t", "a", Map.of("patient_id", "p1"), alerts(), "enc-1").get("insight");

        assertThat(insight).isNotNull();
        assertThat(insight.get("summary")).asString().contains("drug-allergy");
        assertThat(insight.get("prioritised_alert_codes")).isEqualTo(List.of("DRUG_ALLERGY_INTERACTION"));
        assertThat(insight.get("generated_by")).isEqualTo("AI");
        assertThat(insight.get("trace_id")).isNotNull();
    }
}
