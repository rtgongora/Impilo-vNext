package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.core.PaymentService;

/**
 * Closes the refund loop: MusheX publishes {@code REFUND_COMPLETED} / {@code REFUND_FAILED}
 * (both routed to {@code mushex.refund.status.changed}); this consumer resolves the
 * msika-flow refund by its real {@code mushex_refund_id} and finalises the order
 * (REFUNDED, or FAILED honest). Envelope-aware like {@link PaymentEventConsumer} —
 * the same topic also carries COSTA-bill refunds, which are ignored (lookup miss).
 */
@Service
public class MushexRefundEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MushexRefundEventConsumer.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public MushexRefundEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "mushex.refund.status.changed",
                    // v1.1 canonical (envelope) topic
                    "impilo.mushex.refund"
            },
            groupId = "msika-flow-service"
    )
    public void onRefundStatusChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = unwrapPayload(root);

            String mushexRefundId = text(node, "refundId");
            if (mushexRefundId == null) {
                log.warn("Refund event missing refundId");
                return;
            }

            String status = text(node, "status");
            if (status == null) {
                // Envelope forms may carry the event type instead of a status field.
                String eventType = text(root, "eventType");
                if (eventType == null) {
                    eventType = text(node, "eventType");
                }
                status = mapEventType(eventType);
            }
            if (status == null) {
                log.debug("Refund event for {} carries no terminal status; ignoring", mushexRefundId);
                return;
            }

            boolean ours;
            if ("COMPLETED".equalsIgnoreCase(status)) {
                ours = paymentService.completeRefundByMushexId(mushexRefundId);
            } else if ("FAILED".equalsIgnoreCase(status)) {
                ours = paymentService.failRefundByMushexId(mushexRefundId, text(node, "reason"));
            } else {
                // PENDING / REQUESTED — nothing to finalise yet.
                return;
            }

            if (ours) {
                log.info("Processed refund event: mushexRefundId={} status={}", mushexRefundId, status);
            } else {
                log.debug("Refund {} not a msika-flow refund (COSTA-bill refunds share this topic); skipped",
                        mushexRefundId);
            }
        } catch (Exception e) {
            log.error("Failed to process refund event: {}", e.getMessage(), e);
        }
    }

    private static String mapEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "REFUND_COMPLETED" -> "COMPLETED";
            case "REFUND_FAILED" -> "FAILED";
            default -> null;
        };
    }

    private static JsonNode unwrapPayload(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        if (root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
