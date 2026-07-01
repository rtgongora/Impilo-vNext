package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;

/**
 * Builds a FHIR R4 QuestionnaireResponse-shaped projection from a form response's answers. Kept in sync
 * with the frontend {@code fhir-questionnaire-mapping/to-questionnaire-response.ts} (guarded by a golden
 * fixture test) so front-end and back-end emit the same structure for the SHR handoff.
 */
public final class QuestionnaireResponseMapper {

    private QuestionnaireResponseMapper() {}

    public static ObjectNode toQuestionnaireResponse(FormResponseEntity r, JsonNode answers, ObjectMapper mapper) {
        ObjectNode qr = mapper.createObjectNode();
        qr.put("resourceType", "QuestionnaireResponse");
        qr.put("questionnaire", r.getFormKey() + "|" + r.getFormVersion());
        qr.put("status", "completed");
        if (r.getSubjectCpid() != null) {
            qr.putObject("subject").put("reference", "Patient/" + r.getSubjectCpid());
        }
        if (r.getButanoEncounterRef() != null) {
            qr.putObject("encounter").put("reference", "Encounter/" + r.getButanoEncounterRef());
        }
        if (r.getSubmittedAt() != null) {
            qr.put("authored", r.getSubmittedAt().toString());
        }
        qr.putObject("author").put("reference", "Practitioner/" + safe(r.getSubmittedBy(), r.getCreatedBy()));

        ArrayNode items = qr.putArray("item");
        if (answers != null && answers.isObject()) {
            answers.fieldNames().forEachRemaining(linkId -> {
                JsonNode value = answers.get(linkId);
                ObjectNode item = items.addObject();
                item.put("linkId", linkId);
                ObjectNode answer = item.putArray("answer").addObject();
                if (value == null || value.isNull()) {
                    answer.putNull("valueString");
                } else if (value.isBoolean()) {
                    answer.put("valueBoolean", value.booleanValue());
                } else if (value.isNumber()) {
                    answer.put("valueDecimal", value.decimalValue());
                } else if (value.isArray()) {
                    answer.set("valueString", mapper.valueToTree(value.toString()));
                } else {
                    answer.put("valueString", value.asText());
                }
            });
        }
        return qr;
    }

    private static String safe(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : (fallback == null ? "unknown" : fallback);
    }
}
