package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps PCT queue lifecycle events ({@code pct.queue.item.updated}) to
 * patient-facing notifications through the standard {@link NotifyService}
 * pipeline (Mvumo communication-preference gating included).
 *
 * <p>Message content is deliberately neutral: token number, queue movement,
 * and next step only — never a clinical reason, acuity, or escalation
 * rationale. Staff-facing detail stays in the Tshepo audit trail and the
 * queue cockpit.</p>
 *
 * <p>Delivery is IN_APP (inbox): CPID is the platform inbox address. SMS/EMAIL
 * fan-out needs a CPID→contact-point lookup (VITO) which is intentionally not
 * done here. Kafka delivery is at-least-once and notifications carry no
 * dedup key, so a rebalance can duplicate an inbox line; acceptable for
 * informational updates, recorded as a known seam.</p>
 */
@Component
public class QueueLifecycleNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(QueueLifecycleNotificationConsumer.class);

    static final String CHANNEL_IN_APP = "IN_APP";
    static final String MESSAGE_KIND = "REMINDER";

    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    public QueueLifecycleNotificationConsumer(NotifyService notifyService, ObjectMapper objectMapper) {
        this.notifyService = notifyService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "pct.queue.item.updated",
            groupId = "${notification.kafka.queue-events.group-id:notification-service-queue-events}"
    )
    public void onQueueItemEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            NotifyRequest request = toNotifyRequest(event);
            if (request == null) {
                return;
            }
            String tenantId = event.path("tenantId").asText(null);
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Queue event without tenantId — skipping notification (offset={})", record.offset());
                return;
            }
            RequestContext ctx = RequestContext.of(tenantId, "system", null, null, null, null, null);
            notifyService.enqueue(request, ctx);
        } catch (Exception e) {
            // Notification fan-out must never poison the queue-event stream.
            log.warn("Failed to map queue event to notification (offset={}): {}", record.offset(), e.getMessage());
        }
    }

    /**
     * Translate a queue event payload into a neutral notification, or
     * {@code null} when the event is not patient-relevant.
     */
    NotifyRequest toNotifyRequest(JsonNode event) {
        String eventType = event.path("eventType").asText(null);
        String cpid = event.path("patientCpid").asText(null);
        if (eventType == null || cpid == null || cpid.isBlank()) {
            return null;
        }

        Map<String, String> vars = new LinkedHashMap<>();
        String token = event.path("tokenNumber").asText(null);
        if (token != null && !token.isBlank()) {
            vars.put("token", token);
        }

        String templateKey = switch (eventType) {
            case "QUEUE_ITEM_ENQUEUED" -> "QUEUE_CITIZEN_JOINED";
            case "QUEUE_ITEM_CALLED" -> "QUEUE_CITIZEN_CALLED";
            case "QUEUE_ITEM_TRANSFERRED" -> {
                String newToken = event.path("newTokenNumber").asText(null);
                if (newToken != null && !newToken.isBlank()) {
                    vars.put("token", newToken);
                }
                yield "QUEUE_CITIZEN_TRANSFERRED";
            }
            // Escalation reason is staff-facing and must not leak; the patient
            // only learns their case was re-prioritised.
            case "QUEUE_ITEM_ESCALATED" -> "QUEUE_CITIZEN_PRIORITISED";
            case "QUEUE_ITEM_UPDATED" -> switch (event.path("newStatus").asText("")) {
                case "NO_SHOW" -> "QUEUE_CITIZEN_NO_SHOW";
                case "COMPLETED" -> "QUEUE_CITIZEN_COMPLETED";
                case "PAUSED" -> "QUEUE_CITIZEN_ON_HOLD";
                default -> null; // CALLED/IN_SERVICE/IN_TRIAGE etc. — no patient message
            };
            default -> null;
        };

        if (templateKey == null) {
            return null;
        }
        return new NotifyRequest(templateKey, CHANNEL_IN_APP, cpid, vars, cpid, MESSAGE_KIND, cpid, null);
    }
}
