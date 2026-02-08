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
public class PharmacyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PharmacyEventConsumer.class);

    private final PaymentIntentService intentService;
    private final RefundService refundService;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public PharmacyEventConsumer(PaymentIntentService intentService,
                                 RefundService refundService,
                                 IdempotencyRepository idempotencyRepository,
                                 ObjectMapper objectMapper) {
        this.intentService = intentService;
        this.refundService = refundService;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "pharmacy.mushex.charge", groupId = "mushex-service")
    @Transactional
    public void onPharmacyCharge(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "PHARMACY")) {
                ack.acknowledge();
                return;
            }

            String dispenseId = text(event, "dispenseId");
            String tenantId = text(event, "tenantId");
            BigDecimal amount = event.has("amount")
                ? new BigDecimal(event.get("amount").asText())
                : BigDecimal.ZERO;
            String currency = event.has("currency") ? text(event, "currency") : "USD";
            String facilityId = text(event, "facilityId");
            String description = text(event, "description");

            // Create an ADHOC payment intent for the pharmacy charge
            String idempotencyKey = "PHARMACY_CHARGE:" + dispenseId;
            intentService.createIntent(
                SourceType.ADHOC, dispenseId, amount, currency,
                facilityId != null ? UUID.fromString(facilityId) : null,
                idempotencyKey, description
            );

            log.info("Created ADHOC payment intent for pharmacy charge, dispenseId={}, amount={}",
                dispenseId, amount);

            markProcessed(eventId, "PHARMACY");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process pharmacy.mushex.charge", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "pharmacy.mushex.credit", groupId = "mushex-service")
    @Transactional
    public void onPharmacyCredit(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");

            if (isProcessed(eventId, "PHARMACY")) {
                ack.acknowledge();
                return;
            }

            String dispenseId = text(event, "dispenseId");
            String intentId = text(event, "intentId");
            BigDecimal creditAmount = event.has("amount")
                ? new BigDecimal(event.get("amount").asText())
                : BigDecimal.ZERO;
            String reason = text(event, "reason");

            // If intentId is not provided, look up by source
            String resolvedIntentId = intentId;
            if (resolvedIntentId == null && dispenseId != null) {
                resolvedIntentId = intentService.findIntentIdBySource(SourceType.ADHOC, dispenseId);
            }

            if (resolvedIntentId != null) {
                refundService.requestRefund(resolvedIntentId, creditAmount, reason);
                log.info("Created refund for pharmacy credit, dispenseId={}, intentId={}, amount={}",
                    dispenseId, resolvedIntentId, creditAmount);
            } else {
                log.warn("Cannot process pharmacy credit: no payment intent found for dispenseId={}", dispenseId);
            }

            markProcessed(eventId, "PHARMACY");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process pharmacy.mushex.credit", e);
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
