package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.core.AdmissionWorkflow;

import java.util.UUID;

/**
 * Consumes inpatient-service bed-assignment echoes to complete the PCT&lt;-&gt;inpatient admission handshake.
 *
 * <p>PCT owns the admission DECISION (request/approve); inpatient-service owns the physical CENSUS (bed/ward).
 * After PCT emits {@code ADMISSION_APPROVED}, inpatient materialises the census admission and emits
 * {@code inpatient.admission.bed_assigned} carrying the {@code pct_admission_id} and its own
 * {@code admission_ref} + assigned bed. This consumer stamps that link back onto the PCT admission. No
 * clinical field is duplicated — PCT records only the cross-service reference.</p>
 */
@Component
@Profile("!test")
public class InpatientBedAssignedConsumer {

    private static final Logger log = LoggerFactory.getLogger(InpatientBedAssignedConsumer.class);

    private final ObjectMapper objectMapper;
    private final AdmissionWorkflow admissionWorkflow;

    public InpatientBedAssignedConsumer(ObjectMapper objectMapper, AdmissionWorkflow admissionWorkflow) {
        this.objectMapper = objectMapper;
        this.admissionWorkflow = admissionWorkflow;
    }

    @KafkaListener(topics = {"inpatient.admission"}, groupId = "pct-service-admission-link")
    public void onInpatientAdmission(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = firstNonBlank(text(root, "event_type"), text(root, "eventType"));
            if (!"inpatient.admission.bed_assigned".equals(eventType)) {
                return; // only the bed-assignment echo completes the handshake
            }

            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                return;
            }

            String pctAdmissionId = firstNonBlank(text(payload, "pct_admission_id"), text(payload, "pctAdmissionId"));
            String inpatientRef = firstNonBlank(text(payload, "admission_ref"), text(payload, "admissionRef"));
            if (pctAdmissionId == null || inpatientRef == null) {
                log.warn("PCT: inpatient bed_assigned echo missing pct_admission_id or admission_ref, skipping");
                return;
            }

            UUID wardId = parseUuid(firstNonBlank(text(payload, "ward_id"), text(payload, "wardId")));
            UUID bedId = parseUuid(firstNonBlank(text(payload, "bed_id"), text(payload, "bedId")));

            admissionWorkflow.linkInpatientAdmission(
                    UUID.fromString(pctAdmissionId.trim()),
                    UUID.fromString(inpatientRef.trim()),
                    wardId,
                    bedId);
        } catch (JsonProcessingException e) {
            log.error("PCT: failed to parse inpatient admission echo: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("PCT: error handling inpatient admission echo: {}", e.getMessage(), e);
        }
    }

    private JsonNode extractPayload(JsonNode root) {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode p = root.get("payload");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isObject()) {
            return p;
        }
        if (p.isTextual()) {
            try {
                return objectMapper.readTree(p.asText());
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return p;
    }

    private static UUID parseUuid(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
