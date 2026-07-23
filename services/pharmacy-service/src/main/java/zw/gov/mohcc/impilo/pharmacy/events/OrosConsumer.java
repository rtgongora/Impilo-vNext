package zw.gov.mohcc.impilo.pharmacy.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.core.DispenseEngine;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumer for OROS pharmacy orders.
 *
 * <p>Listens to the {@code oros.order.placed} topic and filters for orders
 * with type {@code PHARMACY}. When a qualifying order is received, it
 * creates a corresponding dispense order in the pharmacy service via
 * {@link DispenseEngine#createFromOrosOrder}.</p>
 *
 * <p>Idempotency is enforced by checking whether a dispense order with the
 * same OROS order ID already exists before creating a new one.</p>
 *
 * <h3>OF-B12 claim carriage (§13.3.1)</h3>
 * <p>The {@code oros.order.placed} contract is the serialized OROS
 * {@code OrderEntity} — its shape is frozen. The prescription claim id
 * therefore rides the entity's existing {@code externalRefs} JSON document
 * under the key {@code prescriptionClaimId} (set by the order-placement lane
 * when a claimed prescription drives the order). This consumer extracts it and
 * binds the claim onto the created dispense episode; a top-level
 * {@code prescriptionClaimId} field is also accepted for forward
 * compatibility. No Kafka header is used — headers do not survive the outbox
 * table, whose rows carry only topic/key/payload.</p>
 *
 * <h3>Expected JSON Payload</h3>
 * <pre>{@code
 * {
 *   "orderId": "01HXYZ...",
 *   "tenantId": "uuid",
 *   "facilityId": "uuid",
 *   "patientCpid": "CPID-12345",
 *   "orderType": "PHARMACY",
 *   "priority": "ROUTINE",
 *   "prescriberNotes": "Take with food",
 *   "externalRefs": "{\"prescriptionClaimId\":\"uuid\"}",
 *   "items": [
 *     {
 *       "code": "AMX500",
 *       "displayName": "Amoxicillin 500mg",
 *       "quantity": 30,
 *       "instructions": "1 cap 3x daily"
 *     }
 *   ]
 * }
 * }</pre>
 */
@Service
@Profile("!test")
public class OrosConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrosConsumer.class);

    private final DispenseEngine dispenseEngine;
    private final DispenseOrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the OrosConsumer with required dependencies.
     *
     * @param dispenseEngine  the dispense engine for creating orders
     * @param orderRepository repository for idempotency checks
     * @param objectMapper    Jackson mapper for JSON deserialization
     */
    public OrosConsumer(DispenseEngine dispenseEngine,
                        DispenseOrderRepository orderRepository,
                        ObjectMapper objectMapper) {
        this.dispenseEngine = dispenseEngine;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume OROS order placement events.
     *
     * <p>Filters for PHARMACY order types. Creates a pharmacy dispense order
     * for each qualifying event. Idempotent: skips events for OROS orders
     * that have already been processed.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "oros.order.placed", groupId = "pharmacy-service")
    @Transactional
    public void consumeOrosOrder(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            // Filter: only process PHARMACY order types
            String orderType = getTextField(event, "orderType");
            if (!"PHARMACY".equalsIgnoreCase(orderType)) {
                log.debug("Skipping non-PHARMACY order: type={}", orderType);
                return;
            }

            String orosOrderId = getTextField(event, "orderId");
            if (orosOrderId == null) {
                log.warn("OROS order event missing orderId, skipping");
                return;
            }

            // Idempotency check: skip if already processed
            if (orderRepository.findByOrosOrderId(orosOrderId).isPresent()) {
                log.debug("OROS order {} already processed, skipping", orosOrderId);
                return;
            }

            String tenantId = getTextField(event, "tenantId");
            String facilityId = getTextField(event, "facilityId");
            String patientCpid = getTextField(event, "patientCpid");
            String prescriberNotes = getTextField(event, "prescriberNotes");

            if (tenantId == null || facilityId == null || patientCpid == null) {
                log.warn("OROS order event missing required fields (tenantId, facilityId, or patientCpid), skipping: {}",
                        orosOrderId);
                return;
            }

            // Set up a synthetic TrustContext for the Kafka consumer thread
            TrustContext ctx = new TrustContext(
                    UUID.fromString(tenantId),
                    "OROS-SYSTEM",
                    "SYSTEM",
                    "PHARMACY_DISPENSE",
                    null,
                    UUID.randomUUID(),
                    UUID.fromString(facilityId),
                    null,
                    null,
                    null);
            TrustContextHolder.set(ctx);

            try {
                // Parse order items
                List<DispenseEngine.OrderItemData> items = parseItems(event);

                // OF-B12: extract the prescription claim id (external_refs carriage)
                UUID prescriptionClaimId = parsePrescriptionClaimId(event, orosOrderId);

                dispenseEngine.createFromOrosOrder(
                        orosOrderId,
                        patientCpid,
                        UUID.fromString(facilityId),
                        prescriberNotes,
                        items,
                        prescriptionClaimId);

                log.info("Pharmacy dispense order created from OROS: orosOrderId={}, patientCpid={}, claimId={}",
                        orosOrderId, patientCpid, prescriptionClaimId);
            } finally {
                TrustContextHolder.clear();
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse OROS order event: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Parse the items array from the OROS order JSON payload.
     */
    private List<DispenseEngine.OrderItemData> parseItems(JsonNode event) {
        List<DispenseEngine.OrderItemData> items = new ArrayList<>();
        JsonNode itemsNode = event.get("items");

        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                String code = getTextField(itemNode, "code");
                String displayName = getTextField(itemNode, "displayName");
                int quantity = itemNode.has("quantity") ? itemNode.get("quantity").asInt(0) : 0;
                String instructions = getTextField(itemNode, "instructions");

                if (code != null && quantity > 0) {
                    items.add(new DispenseEngine.OrderItemData(code, displayName, quantity, instructions));
                }
            }
        }

        return items;
    }

    /**
     * OF-B12: extract the prescription claim id carried on the order.
     *
     * <p>Primary carriage is {@code externalRefs} (a JSON document serialized
     * as a string inside the OrderEntity payload) under key
     * {@code prescriptionClaimId}; a top-level field of the same name is also
     * accepted. Malformed values are logged and ignored (the episode is then
     * created unbound and the §13.3.1 anomaly sweep surfaces it) — the
     * medication must still flow.</p>
     */
    private UUID parsePrescriptionClaimId(JsonNode event, String orosOrderId) {
        String raw = getTextField(event, "prescriptionClaimId");
        if (raw == null) {
            String externalRefs = getTextField(event, "externalRefs");
            if (externalRefs != null && !externalRefs.isBlank()) {
                try {
                    JsonNode refs = objectMapper.readTree(externalRefs);
                    raw = getTextField(refs, "prescriptionClaimId");
                } catch (JsonProcessingException e) {
                    log.warn("Unparseable externalRefs on OROS order {}: {}", orosOrderId, e.getMessage());
                }
            }
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed prescriptionClaimId '{}' on OROS order {} — episode created unbound",
                    raw, orosOrderId);
            return null;
        }
    }

    /**
     * Safely extract a text field from a JSON node.
     */
    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
