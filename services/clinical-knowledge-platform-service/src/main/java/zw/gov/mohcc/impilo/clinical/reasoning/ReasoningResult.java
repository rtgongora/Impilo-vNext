package zw.gov.mohcc.impilo.clinical.reasoning;

import java.util.List;
import java.util.Map;

/**
 * Normalised reasoning output. The LLM layer is ADDITIVE: when {@code provenance == "DETERMINISTIC"}
 * the answer/rationale are null and {@code ClinicalAssistantService} keeps its own deterministic
 * composition (the honest floor). When {@code provenance == "LLM"} the validated, grounded answer
 * overrides the summary/rationale and contributes differential considerations.
 */
public record ReasoningResult(
        String provenance, // "LLM" | "DETERMINISTIC"
        String answerSummary, // null when deterministic
        String rationale, // null when deterministic
        List<Map<String, Object>> differentialConsiderations,
        List<String> usedCitationIds,
        boolean insufficientEvidence,
        List<String> safetyFlags,
        String model,
        double groundingRatio,
        boolean groundingValid,
        boolean fellBack,
        String fallbackReason,
        String llmAuditRef,
        Map<String, Object> rawLlmStructuredOutput) {

    /** Deterministic marker — the service uses its own composed answer; no LLM override. */
    public static ReasoningResult deterministic(String reason) {
        return new ReasoningResult(
                "DETERMINISTIC", null, null, List.of(), List.of(), false, List.of(),
                null, 0.0, false, true, reason, null, Map.of());
    }
}
