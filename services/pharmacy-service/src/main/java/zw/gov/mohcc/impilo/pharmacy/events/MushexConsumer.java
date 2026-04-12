package zw.gov.mohcc.impilo.pharmacy.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for MUSHEX payment status events.
 *
 * <p>Listens to the {@code mushex.payment.status.changed} topic. When a
 * payment for a pharmacy-correlated charge is confirmed as PAID, this
 * consumer publishes a PCT discharge blocker clearance event via the
 * outbox, allowing the patient to proceed through the discharge flow.</p>
 *
 * <p>Idempotency is enforced by checking whether the correlation ID
 * (OROS order ID) matches an existing dispense order.</p>
 *
 * <h3>Expected JSON Payload</h3>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "correlationId": "orosOrderId or dispenseOrderId",
 *   "chargeType": "PHARMACY",
 *   "status": "PAID",
 *   "tenantId": "uuid",
 *   "facilityId": "uuid",
 *   "patientCpid": "CPID-12345"
 * }
 * }</pre>
 */
@Service
public class MushexConsumer {

    private static final Logger log = LoggerFactory.getLogger(MushexConsumer.class);

    private final DispenseOrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the MushexConsumer with required dependencies.
     *
     * @param orderRepository  repository for dispense order lookups (idempotency)
     * @param outboxRepository repository for publishing outbox events
     * @param objectMapper     Jackson mapper for JSON deserialization
     */
    public MushexConsumer(DispenseOrderRepository orderRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume MUSHEX payment status change events.
     *
     * <p>When a pharmacy charge is marked as PAID, publishes a PCT
     * discharge blocker clearance event so the PAYMENT blocker is
     * automatically cleared for the patient's discharge workflow.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "mushex.payment.status.changed", groupId = "pharmacy-service")
    @Transactional
    public void consumePaymentStatus(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String chargeType = getTextField(event, "chargeType");
            String status = getTextField(event, "status");
            String correlationId = getTextField(event, "correlationId");
            String tenantId = getTextField(event, "tenantId");
            String facilityId = getTextField(event, "facilityId");
            String patientCpid = getTextField(event, "patientCpid");

            // Only process PHARMACY charges that are PAID
            if (!"PHARMACY".equalsIgnoreCase(chargeType)) {
                log.debug("Skipping non-PHARMACY payment event: chargeType={}", chargeType);
                return;
            }

            if (!"PAID".equalsIgnoreCase(status)) {
                log.debug("Skipping non-PAID payment event: status={}", status);
                return;
            }

            if (correlationId == null || tenantId == null) {
                log.warn("MUSHEX payment event missing correlationId or tenantId, skipping");
                return;
            }

            // Idempotency: check if this correlation matches a known dispense order
            boolean knownOrder = orderRepository.findByOrosOrderId(correlationId).isPresent();
            if (!knownOrder) {
                log.debug("No matching dispense order for MUSHEX correlation {}, skipping", correlationId);
                return;
            }

            // Publish PCT discharge blocker clearance event
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("correlationId", correlationId);
            payload.put("blockerType", "PAYMENT");
            payload.put("action", "CLEAR");
            payload.put("source", "PHARMACY");
            payload.put("tenantId", tenantId);
            payload.put("facilityId", facilityId);
            payload.put("patientCpid", patientCpid);
            payload.put("chargeStatus", status);

            publishEvent("PCT_BLOCKER", correlationId,
                    "PCT_BLOCKER_CLEARED", payload, UUID.fromString(tenantId));

            log.info("PCT PAYMENT blocker cleared for pharmacy charge: correlationId={}, patientCpid={}",
                    correlationId, patientCpid);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse MUSHEX payment event: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Safely extract a text field from a JSON node.
     */
    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }

    /**
     * Write a domain event to the transactional outbox.
     */
    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
