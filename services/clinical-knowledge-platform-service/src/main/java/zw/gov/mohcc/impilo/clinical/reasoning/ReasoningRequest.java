package zw.gov.mohcc.impilo.clinical.reasoning;

import java.util.List;
import java.util.Map;

/**
 * Immutable input bundle for clinical reasoning. Everything here was already computed deterministically
 * by {@code ClinicalAssistantService.ask(...)} — retrieved guidance citations, structured medicine
 * guidance, the authoritative deterministic rule alerts, and the patient context. The reasoner must
 * reason ONLY over this; it never fetches more or invents context.
 */
public record ReasoningRequest(
        String tenantId,
        String actorId,
        String role,
        boolean citizenMode,
        String question,
        /** section_id, page, section_title, excerpt, document_version */
        List<Map<String, Object>> citations,
        /** name, generic, dose, route, frequency, level_of_care, ... */
        List<Map<String, Object>> medicineGuidance,
        /** RuleAlert.toMap(): code, severity, message, explanation — AUTHORITATIVE, never altered */
        List<Map<String, Object>> safetyAlerts,
        Map<String, Object> patientContext,
        String sourceVersion,
        String knowledgeVersion) {
}
