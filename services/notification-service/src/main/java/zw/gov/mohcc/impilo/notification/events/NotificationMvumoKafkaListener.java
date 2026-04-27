package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.notification.service.NotificationQueuePreferenceService;

/**
 * When Mvumo publishes to {@code platform.mvumo.events} (or configured topic) after a communication
 * preference change, we cancel PENDING multi-channel queue rows for that patient so the worker does not send
 * with stale policy.
 */
@Component
@ConditionalOnProperty(name = "impilo.kafka.mvumo.enabled", havingValue = "true")
public class NotificationMvumoKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationMvumoKafkaListener.class);

    private static final String PREFIX = "impilo.mvumo.communication_preference.";

    private final ObjectMapper objectMapper;
    private final NotificationQueuePreferenceService notificationQueuePreferenceService;

    public NotificationMvumoKafkaListener(
            ObjectMapper objectMapper, NotificationQueuePreferenceService notificationQueuePreferenceService) {
        this.objectMapper = objectMapper;
        this.notificationQueuePreferenceService = notificationQueuePreferenceService;
    }

        @KafkaListener(
            topics = "${impilo.kafka.mvumo.topic:platform.mvumo.events}",
            groupId = "${impilo.kafka.mvumo.group-id:notification-service-mvumo}")
    @Transactional
    public void onMvumoEvent(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String eventType = text(root, "eventType");
            if (eventType == null || !eventType.startsWith(PREFIX)) {
                return;
            }
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()) {
                return;
            }
            String tenantId = text(payload, "tenantId");
            String patientRef = text(payload, "patientRef");
            if (tenantId == null || patientRef == null) {
                return;
            }
            int n = notificationQueuePreferenceService.cancelPendingForPatient(tenantId, patientRef, eventType);
            if (n > 0) {
                log.info("Cancelled {} PENDING notification(s) for patientRef={} after {}", n, patientRef, eventType);
            }
        } catch (Exception e) {
            log.warn("Failed to process Mvumo Kafka message: {}", e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).asText();
    }
}
