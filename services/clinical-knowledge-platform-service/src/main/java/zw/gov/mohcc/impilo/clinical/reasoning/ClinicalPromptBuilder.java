package zw.gov.mohcc.impilo.clinical.reasoning;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the hardened, closed-book system instruction, the serialized CONTEXT user message, and the
 * structured-output JSON schema for grounded clinical reasoning. The model is instructed to reason
 * ONLY over supplied context, cite only supplied section ids, never alter safety alerts, and declare
 * insufficient evidence rather than fabricate.
 */
@Component
public class ClinicalPromptBuilder {

    private static final String SYSTEM = """
            You are a grounded clinical decision-support reasoner for Zimbabwe's EDLIZ national guidance.
            You assist a licensed clinician; you NEVER override clinical judgement.

            HARD RULES:
            - Reason ONLY over the CONTEXT provided in the user message (SECTIONS, MEDICINE_GUIDANCE,
              SAFETY_ALERTS, PATIENT_CONTEXT). Do NOT use outside medical knowledge.
            - Do NOT introduce drugs, doses, diagnoses, or facts that are absent from CONTEXT.
            - Cite only section_id values that appear in SECTIONS. Put every id you relied on in
              used_citation_ids.
            - If CONTEXT does not contain enough evidence to answer, set insufficient_evidence=true and
              do not fabricate an answer or recommendation.
            - SAFETY_ALERTS are authoritative and supplied for context only. Never remove, soften, or
              contradict them. You may restate them in safety_flags.
            - Citizen mode: use plain educational language and avoid prescriber-only directives.
            - Be concise and clinically precise. Output MUST match the provided JSON schema.
            """;

    public List<Map<String, String>> assistantMessages(ReasoningRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUESTION:\n").append(req.question() == null ? "" : req.question()).append("\n\n");
        sb.append("MODE: ").append(req.citizenMode() ? "CITIZEN_EDUCATION" : "LICENSED_CLINICIAN").append("\n\n");

        sb.append("SECTIONS (the ONLY evidence you may cite):\n");
        if (req.citations() == null || req.citations().isEmpty()) {
            sb.append("(none retrieved)\n");
        } else {
            for (Map<String, Object> c : req.citations()) {
                sb.append("- section_id=").append(c.get("section_id"))
                        .append(" | page=").append(c.get("page"))
                        .append(" | title=").append(c.get("section_title")).append("\n")
                        .append("  excerpt: ").append(c.get("excerpt")).append("\n");
            }
        }

        sb.append("\nMEDICINE_GUIDANCE:\n");
        if (req.medicineGuidance() == null || req.medicineGuidance().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Map<String, Object> m : req.medicineGuidance()) {
                sb.append("- ").append(m).append("\n");
            }
        }

        sb.append("\nSAFETY_ALERTS (authoritative; do not alter):\n");
        if (req.safetyAlerts() == null || req.safetyAlerts().isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Map<String, Object> a : req.safetyAlerts()) {
                sb.append("- [").append(a.get("severity")).append("] ")
                        .append(a.get("code")).append(": ").append(a.get("message")).append("\n");
            }
        }

        sb.append("\nPATIENT_CONTEXT:\n").append(req.patientContext() == null ? "{}" : req.patientContext()).append("\n");

        return List.of(
                Map.of("role", "system", "content", SYSTEM),
                Map.of("role", "user", "content", sb.toString()));
    }

    /** Structured-output JSON schema for grounded clinical reasoning. */
    public Map<String, Object> assistantSchema() {
        Map<String, Object> diffItem = obj(
                Map.of(
                        "consideration", prop("string"),
                        "citation_refs", arr("string"),
                        "confidence", enumProp("LOW", "MODERATE", "HIGH")),
                List.of("consideration", "citation_refs", "confidence"));

        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("type", List.of("object", "null"));
        recommendation.put("properties", Map.of(
                "text", prop("string"),
                "citation_refs", arr("string")));
        recommendation.put("additionalProperties", false);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("answer_summary", prop("string"));
        props.put("differential_considerations", arrOf(diffItem));
        props.put("recommendation", recommendation);
        props.put("used_citation_ids", arr("string"));
        props.put("insufficient_evidence", prop("boolean"));
        props.put("safety_flags", arr("string"));

        return obj(props, List.of(
                "answer_summary", "differential_considerations", "recommendation",
                "used_citation_ids", "insufficient_evidence", "safety_flags"));
    }

    // ── tiny JSON-schema helpers ─────────────────────────────────────────
    private static Map<String, Object> prop(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> enumProp(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> arr(String itemType) {
        return Map.of("type", "array", "items", Map.of("type", itemType));
    }

    private static Map<String, Object> arrOf(Map<String, Object> itemSchema) {
        return Map.of("type", "array", "items", itemSchema);
    }

    private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        m.put("additionalProperties", false);
        return m;
    }
}
