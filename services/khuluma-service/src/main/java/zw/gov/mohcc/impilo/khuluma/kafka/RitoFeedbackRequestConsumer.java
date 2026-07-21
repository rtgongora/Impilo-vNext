package zw.gov.mohcc.impilo.khuluma.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService;
import zw.gov.mohcc.impilo.khuluma.core.DeliveryService.DispatchContent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes Rito's post-visit feedback-request signal ({@code rito.feedback.requested}) and
 * dispatches the request across channels. Rito owns the "request feedback" decision; Khuluma
 * owns delivery; notification-service (template-driven) owns recipient + comm-preference
 * resolution from {@code patientRef}. Fail-soft: a bad event is logged, never poisons the listener.
 */
@Component
public class RitoFeedbackRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(RitoFeedbackRequestConsumer.class);
    private static final List<String> FEEDBACK_CHANNELS = List.of("SMS", "WHATSAPP", "IN_APP");

    private final ObjectMapper objectMapper;
    private final DeliveryService deliveryService;

    public RitoFeedbackRequestConsumer(ObjectMapper objectMapper, DeliveryService deliveryService) {
        this.objectMapper = objectMapper;
        this.deliveryService = deliveryService;
    }

    @KafkaListener(
            topics = "${impilo.khuluma.rito.feedback-request-topic:rito.feedback.request}",
            groupId = "${spring.kafka.consumer.group-id:khuluma-service}")
    public void onFeedbackRequested(String payload) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            String tenant = text(n, "tenantId");
            String patientCpid = text(n, "patientCpid");
            String encounterRef = text(n, "encounterRef");
            if (tenant == null || encounterRef == null) {
                log.warn("Skipping feedback request — missing tenantId/encounterRef");
                return;
            }
            String templateKey = text(n, "templateKey");
            String messageKind = text(n, "messageKind") != null ? text(n, "messageKind") : "POST_VISIT_FEEDBACK";

            // recipient/patientRef = the patient CPID: notification-service resolves the actual
            // contact address + communication preferences from patientRef (Khuluma does not hold PII).
            // feedbackPath = the canonical in-app deep link the template turns into the tap target
            // (one-ui-shell /feedback/visit/{encounterRef}); the page resolves the provider from
            // the encounter and records the verified rating.
            DispatchContent content = new DispatchContent(
                    templateKey,
                    Map.of("encounterRef", encounterRef,
                            "facilityId", text(n, "facilityId") != null ? text(n, "facilityId") : "",
                            "feedbackPath", "/feedback/visit/" + encounterRef),
                    patientCpid,
                    messageKind);
            deliveryService.dispatch(UUID.fromString(tenant), encounterRef, patientCpid, FEEDBACK_CHANNELS, content);
            log.info("Dispatched post-visit feedback request for encounter={} (template={})", encounterRef, templateKey);
        } catch (Exception e) {
            log.error("Failed to dispatch feedback request: {}", e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }
}
