package zw.gov.mohcc.impilo.mushex.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.IdempotencyEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MsikaFlowEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MsikaFlowEventConsumer.class);

    private final PaymentIntentService intentService;
    private final RefundService refundService;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public MsikaFlowEventConsumer(PaymentIntentService intentService,
                                  RefundService refundService,
                                  IdempotencyRepository idempotencyRepository,
                                  ObjectMapper objectMapper) {
        this.intentService = intentService;
        this.refundService = refundService;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "msika.flow.order.paid", groupId = "mushex-service")
    @Transactional
    public void onOrderPaid(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "MSIKA")) {
                ack.acknowledge();
                return;
            }

            String orderId = text(event, "orderId");
            String tenantId = text(event, "tenantId");
            BigDecimal amount = event.has("totalAmount")
                ? new BigDecimal(event.get("totalAmount").asText())
                : BigDecimal.ZERO;
            String currency = event.has("currency") ? text(event, "currency") : "USD";
            String facilityId = text(event, "facilityId");

            // Create a payment intent for the MSIKA Flow order
            String idempotencyKey = "MSIKA_ORDER:" + orderId;
            intentService.createIntent(
                SourceType.MSIKA_ORDER, orderId, amount, currency,
                facilityId != null ? UUID.fromString(facilityId) : null,
                idempotencyKey, null
            );

            log.info("Created payment intent for MSIKA Flow order {}", orderId);

            markProcessed(eventId, "MSIKA");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process msika.flow.order.paid", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "msika.flow.refund.requested", groupId = "mushex-service")
    @Transactional
    public void onRefundRequested(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "MSIKA")) {
                ack.acknowledge();
                return;
            }

            String orderId = text(event, "orderId");
            String intentId = text(event, "intentId");
            BigDecimal refundAmount = event.has("refundAmount")
                ? new BigDecimal(event.get("refundAmount").asText())
                : BigDecimal.ZERO;
            String reason = text(event, "reason");

            // If intentId is not provided, look up by source
            String resolvedIntentId = intentId;
            if (resolvedIntentId == null && orderId != null) {
                resolvedIntentId = intentService.findIntentIdBySource(SourceType.MSIKA_ORDER, orderId);
            }

            if (resolvedIntentId != null) {
                refundService.requestRefund(resolvedIntentId, refundAmount, reason);
                log.info("Triggered refund for MSIKA Flow order {}, intentId={}, amount={}",
                    orderId, resolvedIntentId, refundAmount);
            } else {
                log.warn("Cannot process MSIKA refund: no payment intent found for order {}", orderId);
            }

            markProcessed(eventId, "MSIKA");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process msika.flow.refund.requested", e);
            ack.acknowledge();
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private boolean isProcessed(String eventId, String source) {
        if (eventId == null) return false;
        return idempotencyRepository.existsById(source + ":" + eventId);
    }

    private void markProcessed(String eventId, String source) {
        if (eventId == null) return;
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(source + ":" + eventId);
        entity.setSourceSystem(source);
        entity.setProcessedAt(OffsetDateTime.now());
        idempotencyRepository.save(entity);
    }
}
