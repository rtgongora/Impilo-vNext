package zw.gov.mohcc.impilo.clinical.reasoning;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingValidatorTest {

    private final GroundingValidator validator = new GroundingValidator();

    @Test
    void groundedAnswerCitingRealSections_isValid() {
        Map<String, Object> out = Map.of(
                "answer_summary", "Use amoxicillin per EDLIZ.",
                "differential_considerations", List.of(Map.of("consideration", "CAP", "citation_refs", List.of("sec-1"), "confidence", "HIGH")),
                "recommendation", Map.of("text", "amoxicillin", "citation_refs", List.of("sec-1")),
                "used_citation_ids", List.of("sec-1"),
                "insufficient_evidence", false,
                "safety_flags", List.of());
        var r = validator.validate(out, Set.of("sec-1", "sec-2"), 0.5);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void hallucinatedCitation_isInvalid() {
        Map<String, Object> out = Map.of(
                "answer_summary", "x", "differential_considerations", List.of(),
                "recommendation", Map.of(), "used_citation_ids", List.of("sec-DOES-NOT-EXIST"),
                "insufficient_evidence", false, "safety_flags", List.of());
        var r = validator.validate(out, Set.of("sec-1"), 0.5);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).startsWith("hallucinated-citation");
    }

    @Test
    void answerWithEmptyRetrieval_isInvalid() {
        Map<String, Object> out = Map.of(
                "answer_summary", "I think it is pneumonia", "differential_considerations", List.of(),
                "recommendation", Map.of(), "used_citation_ids", List.of(),
                "insufficient_evidence", false, "safety_flags", List.of());
        var r = validator.validate(out, Set.of(), 0.5);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("answered-without-sources");
    }

    @Test
    void insufficientEvidenceWithEmptyRetrieval_isValid() {
        Map<String, Object> out = Map.of(
                "answer_summary", "", "differential_considerations", List.of(),
                "recommendation", Map.of(), "used_citation_ids", List.of(),
                "insufficient_evidence", true, "safety_flags", List.of());
        var r = validator.validate(out, Set.of(), 0.5);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void substantiveAnswerWithNoCitations_isInvalid() {
        Map<String, Object> out = Map.of(
                "answer_summary", "Definitely treat with X", "differential_considerations", List.of(),
                "recommendation", Map.of(), "used_citation_ids", List.of(),
                "insufficient_evidence", false, "safety_flags", List.of());
        var r = validator.validate(out, Set.of("sec-1", "sec-2"), 0.5);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("uncited-answer");
    }

    @Test
    void missingRequiredField_isInvalid() {
        var r = validator.validate(Map.of("answer_summary", "x"), Set.of("sec-1"), 0.5);
        assertThat(r.valid()).isFalse();
    }
}
