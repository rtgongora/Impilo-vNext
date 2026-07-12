package zw.gov.mohcc.impilo.mushex.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observes Msika Flow money events. Intent creation and refund execution are now
 * driven by Msika Flow's <em>synchronous</em> HTTP handoff
 * ({@code MushexClient} → {@code POST /mushex/v1/payment-intents} and
 * {@code POST /mushex/v1/payment-intents/{id}/refund}), so these Kafka listeners are
 * pure log-only observers — the exact retirement COSTA's
 * {@code CostaEventConsumer.onBillFinalized} received for the identical reason.
 *
 * <p>The previous implementation was semantically backwards and could never work:</p>
 * <ul>
 *   <li>it created a payment intent on {@code msika.flow.order.paid} — but the order
 *       is only "paid" <em>after</em> MusheX confirms the intent, so this both
 *       inverted the lifecycle and would have double-created intents alongside the
 *       real HTTP handoff (both keyed {@code MSIKA_ORDER:<orderId>});</li>
 *   <li>{@code onRefundRequested} triggered a second MusheX refund for a refund Msika
 *       Flow had already requested over HTTP — a double refund.</li>
 * </ul>
 *
 * <p>Kept as observers (not deleted) so the topics stay wired for audit/metrics and
 * so re-enabling a Kafka path later is a deliberate, reviewed change.</p>
 */
@Service
public class MsikaFlowEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MsikaFlowEventConsumer.class);

    private final ObjectMapper objectMapper;

    public MsikaFlowEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "msika.flow.order.paid", groupId = "mushex-service")
    @Transactional
    public void onOrderPaid(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            log.info("msika.flow.order.paid observed for order {} (intent creation is Msika-Flow-driven via HTTP handoff)",
                    text(event, "orderId"));
        } catch (Exception e) {
            log.error("Failed to read msika.flow.order.paid", e);
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "msika.flow.refund.requested", groupId = "mushex-service")
    @Transactional
    public void onRefundRequested(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            log.info("msika.flow.refund.requested observed for order {} (refund execution is Msika-Flow-driven via HTTP handoff)",
                    text(event, "orderId"));
        } catch (Exception e) {
            log.error("Failed to read msika.flow.refund.requested", e);
        } finally {
            ack.acknowledge();
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
