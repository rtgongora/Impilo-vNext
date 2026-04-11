package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalKnowledgeControllerPrescribingMergeTest {

    @Test
    void defaultsRecordTraceTrueWhenAbsent() {
        Map<String, Object> merged = ClinicalKnowledgeController.mergePrescribingEvaluateBody(null, null, Map.of("patient_id", "p1"));
        assertThat(merged.get("record_trace")).isEqualTo(true);
    }

    @Test
    void skipHeaderForcesFalse() {
        Map<String, Object> merged = ClinicalKnowledgeController.mergePrescribingEvaluateBody(
                "true", null, new LinkedHashMap<>(Map.of("record_trace", true)));
        assertThat(merged.get("record_trace")).isEqualTo(false);
    }

    @Test
    void tracePolicyHeaderOverridesBody() {
        Map<String, Object> merged = ClinicalKnowledgeController.mergePrescribingEvaluateBody(
                null, "false", new LinkedHashMap<>(Map.of("record_trace", true)));
        assertThat(merged.get("record_trace")).isEqualTo(false);
    }
}
