package zw.gov.mohcc.impilo.scheduling.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.scheduling.core.SurgicalWaitlistService;

import java.util.UUID;

/**
 * The single trigger from referral into scheduling. On referral.surgical.accepted
 * it places the case on the waitlist — idempotently by referral_id, so redelivery
 * (at-least-once Kafka) is safe. Malformed events are treated as poison pills
 * (logged + acked) and never crash the consumer.
 */
@Component
@Profile("!test")
public class SchedulingReferralConsumer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingReferralConsumer.class);

    private final SurgicalWaitlistService waitlistService;
    private final ObjectMapper objectMapper;

    public SchedulingReferralConsumer(SurgicalWaitlistService waitlistService, ObjectMapper objectMapper) {
        this.waitlistService = waitlistService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"referral.surgical.accepted"}, groupId = "scheduling-service")
    public void onSurgicalReferralAccepted(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = extractPayload(root);
            String tenantId = text(payload, "tenantId", "tenant_id");
            String referralId = text(payload, "referralId", "referral_id");
            String patientId = text(payload, "patientId", "patient_id");
            if (tenantId == null || referralId == null || patientId == null) {
                log.warn("SCHEDULING: surgical.accepted missing required fields, skipping (poison pill)");
                return;
            }
            UUID tenantUuid;
            UUID referralUuid;
            try {
                tenantUuid = UUID.fromString(tenantId.trim());
                referralUuid = UUID.fromString(referralId.trim());
            } catch (IllegalArgumentException e) {
                log.warn("SCHEDULING: surgical.accepted has malformed UUID, skipping (poison pill): {}",
                        e.getMessage());
                return;
            }
            String zibo = text(payload, "intendedProcedureZiboCode", "zibo");
            String priority = text(payload, "clinicalPriority", "clinical_priority");
            waitlistService.placeOnWaitlist(tenantUuid, referralUuid, patientId, zibo,
                    priority != null ? priority : "P4", null, null);
            log.info("SCHEDULING: placed surgical referral {} on waitlist (patient={})", referralId, patientId);
        } catch (Exception e) {
            log.error("SCHEDULING: failed to process surgical.accepted event, skipping: {}", e.getMessage(), e);
        }
    }

    private JsonNode extractPayload(JsonNode root) {
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            return root;
        }
        if (payload.isTextual()) {
            try {
                return objectMapper.readTree(payload.asText());
            } catch (Exception e) {
                return root;
            }
        }
        return payload;
    }

    private String text(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
