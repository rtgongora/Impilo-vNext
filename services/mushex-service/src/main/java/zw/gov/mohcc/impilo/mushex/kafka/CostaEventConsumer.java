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
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CostaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostaEventConsumer.class);

    private final PaymentIntentService intentService;
    private final RefundService refundService;
    private final PaymentIntentRepository intentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public CostaEventConsumer(PaymentIntentService intentService,
                              RefundService refundService,
                              PaymentIntentRepository intentRepository,
                              IdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.intentService = intentService;
        this.refundService = refundService;
        this.intentRepository = intentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "costa.bill.finalized", groupId = "mushex-service")
    @Transactional
    public void onBillFinalized(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "COSTA")) {
                ack.acknowledge();
                return;
            }

            String billId = text(event, "billId");

            // Intent creation moved to COSTA's synchronous handoff
            // (PaymentIntegrationService -> MushexPaymentIntentClient): this
            // consumer could never work — the BILL_FINALIZED payload lacked
            // the fields it read (amount resolved to 0, facility to null) and
            // TrustContextHolder.require() throws on the Kafka thread. It
            // would also double-bill the FULL bill (not the patient
            // shortfall) once listeners were enabled. Kept as an observer.
            log.info("costa.bill.finalized observed for bill {} (intent creation is COSTA-driven)", billId);

            markProcessed(eventId, "COSTA");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process costa.bill.finalized", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "costa.invoice.issued", groupId = "mushex-service")
    @Transactional
    public void onInvoiceIssued(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "COSTA_INV")) {
                ack.acknowledge();
                return;
            }

            log.info("Received costa.invoice.issued event: {}", text(event, "invoiceId"));
            markProcessed(eventId, "COSTA_INV");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process costa.invoice.issued", e);
            ack.acknowledge();
        }
    }

    /**
     * When COSTA creates a refund on a bill, find the linked MusheX payment
     * intent and create a corresponding refund request.
     */
    @KafkaListener(topics = "costa.refund.issued", groupId = "mushex-service")
    @Transactional
    public void onRefundIssued(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "COSTA_REFUND")) {
                ack.acknowledge();
                return;
            }

            String billId = text(event, "billId");
            String costaRefundId = text(event, "refundId");
            BigDecimal amount = event.has("amount")
                    ? new BigDecimal(event.get("amount").asText()) : BigDecimal.ZERO;
            String reason = text(event, "reason");

            // Find the MusheX payment intent linked to this bill
            var intents = intentRepository.findBySourceTypeAndSourceId(SourceType.COSTA_BILL, billId);
            PaymentIntentEntity paidIntent = intents.stream()
                    .filter(i -> i.getStatus() == IntentStatus.PAID)
                    .findFirst()
                    .orElse(null);

            if (paidIntent == null) {
                log.warn("COSTA refund {}: no PAID intent found for bill {}, skipping",
                        costaRefundId, billId);
                markProcessed(eventId, "COSTA_REFUND");
                ack.acknowledge();
                return;
            }

            // Create the refund on the MusheX payment intent
            String refundReason = (reason != null ? reason : "COSTA refund") + " [COSTA:" + costaRefundId + "]";
            refundService.requestRefund(paidIntent.getIntentId(), amount, refundReason);

            log.info("Created MusheX refund for COSTA refund {} on intent {} (bill {})",
                    costaRefundId, paidIntent.getIntentId(), billId);

            markProcessed(eventId, "COSTA_REFUND");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process costa.refund.issued", e);
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
