package zw.gov.mohcc.impilo.clinical.reasoning;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.llm.LlmClinicalClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmClinicalReasonerTest {

    private final LlmClinicalClient client = mock(LlmClinicalClient.class);
    private final LlmClinicalReasoner reasoner = new LlmClinicalReasoner(
            client, new ClinicalPromptBuilder(), new GroundingValidator(), new DeterministicClinicalReasoner(), 0.5);

    private ReasoningRequest requestWithSection() {
        Map<String, Object> citation = Map.of(
                "section_id", "sec-1", "page", 142, "section_title", "Respiratory", "excerpt", "CAP first line", "document_version", "EDLIZ-2025");
        return new ReasoningRequest("t1", "actor-1", "PROVIDER", false, "adult cough and fever",
                List.of(citation), List.of(), List.of(), Map.of(), "EDLIZ-2025", "ckp-seed-1");
    }

    private void stubLlm(Map<String, Object> structuredOutput) {
        when(client.requestStructured(anyString(), any(), any(), any(), anyString(), anyDouble()))
                .thenReturn(Optional.of(new LlmClinicalClient.LlmStructuredResult(
                        "anthropic", "claude-sonnet", structuredOutput, false, "audit-1")));
    }

    @Test
    void groundedSuccess_yieldsLlmProvenance() {
        stubLlm(Map.of(
                "answer_summary", "Community-acquired pneumonia: amoxicillin first-line per EDLIZ.",
                "differential_considerations", List.of(Map.of("consideration", "CAP", "citation_refs", List.of("sec-1"), "confidence", "HIGH")),
                "recommendation", Map.of("text", "amoxicillin", "citation_refs", List.of("sec-1")),
                "used_citation_ids", List.of("sec-1"),
                "insufficient_evidence", false,
                "safety_flags", List.of()));

        ReasoningResult r = reasoner.reason(requestWithSection());

        assertThat(r.provenance()).isEqualTo("LLM");
        assertThat(r.answerSummary()).contains("pneumonia");
        assertThat(r.fellBack()).isFalse();
        assertThat(r.model()).isEqualTo("claude-sonnet");
        assertThat(r.differentialConsiderations()).hasSize(1);
    }

    @Test
    void hallucinatedCitation_fallsBackToDeterministic() {
        stubLlm(Map.of(
                "answer_summary", "Treat with mystery drug.",
                "differential_considerations", List.of(),
                "recommendation", Map.of(),
                "used_citation_ids", List.of("sec-FAKE"),
                "insufficient_evidence", false,
                "safety_flags", List.of()));

        ReasoningResult r = reasoner.reason(requestWithSection());

        assertThat(r.provenance()).isEqualTo("DETERMINISTIC");
        assertThat(r.fellBack()).isTrue();
        assertThat(r.fallbackReason()).contains("hallucinated-citation");
        assertThat(r.answerSummary()).isNull(); // service keeps its own deterministic answer
    }

    @Test
    void llmInsufficientEvidence_fallsBackToDeterministic() {
        stubLlm(Map.of(
                "answer_summary", "", "differential_considerations", List.of(), "recommendation", Map.of(),
                "used_citation_ids", List.of(), "insufficient_evidence", true, "safety_flags", List.of()));

        ReasoningResult r = reasoner.reason(requestWithSection());

        assertThat(r.provenance()).isEqualTo("DETERMINISTIC");
        assertThat(r.fellBack()).isTrue();
        assertThat(r.fallbackReason()).contains("insufficient");
    }

    @Test
    void llmUnavailableOrStub_fallsBackToDeterministic() {
        when(client.requestStructured(anyString(), any(), any(), any(), anyString(), anyDouble()))
                .thenReturn(Optional.empty());

        ReasoningResult r = reasoner.reason(requestWithSection());

        assertThat(r.provenance()).isEqualTo("DETERMINISTIC");
        assertThat(r.fellBack()).isTrue();
        assertThat(r.fallbackReason()).isEqualTo("llm-unavailable-or-stub");
    }
}
