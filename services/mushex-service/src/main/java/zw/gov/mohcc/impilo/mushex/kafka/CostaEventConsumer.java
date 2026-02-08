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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CostaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostaEventConsumer.class);

    private final PaymentIntentService intentService;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public CostaEventConsumer(PaymentIntentService intentService,
                              IdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.intentService = intentService;
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
            String tenantId = text(event, "tenantId");
            BigDecimal amount = event.has("totalAmount")
                ? new BigDecimal(event.get("totalAmount").asText())
                : BigDecimal.ZERO;
            String currency = event.has("currency") ? text(event, "currency") : "USD";
            String facilityId = text(event, "facilityId");

            // Create a payment intent for the finalized bill
            String idempotencyKey = "COSTA_BILL:" + billId;
            intentService.createIntent(
                SourceType.COSTA_BILL, billId, amount, currency,
                facilityId != null ? UUID.fromString(facilityId) : null,
                idempotencyKey, null
            );

            log.info("Created payment intent for COSTA bill {}", billId);

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
