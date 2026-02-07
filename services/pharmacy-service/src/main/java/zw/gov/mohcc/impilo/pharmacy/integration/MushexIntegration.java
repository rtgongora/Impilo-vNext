package zw.gov.mohcc.impilo.pharmacy.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integration service for the MUSHEX billing/finance service.
 *
 * <p>Publishes billing events (charges and credit notes) via the transactional
 * outbox pattern. Events are written to the {@code rx_event_outbox} table
 * within the current transaction and later published to Kafka by the
 * outbox publisher.</p>
 *
 * <p>This pattern guarantees at-least-once delivery and transactional
 * consistency between the dispense state change and the billing event.</p>
 */
@Service
public class MushexIntegration {

    private static final Logger log = LoggerFactory.getLogger(MushexIntegration.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public MushexIntegration(EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Emit a charge event to MUSHEX for dispensed items.
     *
     * <p>Creates an outbox entry with event type {@code MUSHEX_CHARGE_REQUESTED}
     * that will be published to the {@code pharmacy.mushex.charge} Kafka topic.</p>
     *
     * @param orderId   the dispense order ID
     * @param itemCode  the drug/item code
     * @param qty       the quantity dispensed
     * @param unitPrice the unit price for billing
     * @param tenantId  the tenant ID
     */
    public void emitChargeEvent(UUID orderId, String itemCode, int qty, String unitPrice, UUID tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("itemCode", itemCode);
        payload.put("qty", qty);
        payload.put("unitPrice", unitPrice);
        payload.put("source", "pharmacy-service");

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("DISPENSE_ORDER");
        event.setAggregateId(orderId.toString());
        event.setEventType("MUSHEX_CHARGE_REQUESTED");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId);

        outboxRepository.save(event);

        log.info("MUSHEX charge event queued: orderId={}, itemCode={}, qty={}", orderId, itemCode, qty);
    }

    /**
     * Emit a credit note event to MUSHEX for reversals.
     *
     * <p>Creates an outbox entry with event type {@code MUSHEX_CREDIT_NOTE}
     * that will be published to the {@code pharmacy.mushex.credit} Kafka topic.</p>
     *
     * @param orderId  the dispense order ID
     * @param itemCode the drug/item code
     * @param qty      the quantity being credited
     * @param reason   the reason for the credit note
     * @param tenantId the tenant ID
     */
    public void emitCreditNote(UUID orderId, String itemCode, int qty, String reason, UUID tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("itemCode", itemCode);
        payload.put("qty", qty);
        payload.put("reason", reason);
        payload.put("source", "pharmacy-service");

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("DISPENSE_ORDER");
        event.setAggregateId(orderId.toString());
        event.setEventType("MUSHEX_CREDIT_NOTE");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId);

        outboxRepository.save(event);

        log.info("MUSHEX credit note event queued: orderId={}, itemCode={}, qty={}, reason={}",
                orderId, itemCode, qty, reason);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload", e);
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
