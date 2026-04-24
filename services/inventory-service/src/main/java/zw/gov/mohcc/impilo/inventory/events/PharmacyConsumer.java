package zw.gov.mohcc.impilo.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Kafka consumer for pharmacy stock movement requests.
 *
 * <p>Listens to the {@code pharmacy.stock.movement.requested} topic and
 * posts consumption events to the inventory ledger via
 * {@link ConsumptionPostingService#postPharmacyConsumption}.</p>
 *
 * <p>Sets up a synthetic {@link TrustContext} from the event headers
 * so that downstream services have the trust context available on
 * the Kafka consumer thread.</p>
 *
 * <h3>Expected JSON Payload</h3>
 * <pre>{@code
 * {
 *   "tenantId": "uuid",
 *   "facilityId": "uuid",
 *   "storeId": "uuid",
 *   "itemCode": "PARA-500",
 *   "batch": "BATCH-001",
 *   "expiry": "2027-06-30",
 *   "qty": 30,
 *   "orderId": "OROS-ORDER-001",
 *   "actorId": "pharmacist-001"
 * }
 * }</pre>
 */
@Service
@ConditionalOnProperty(prefix = "inventory.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class PharmacyConsumer {

    private static final Logger log = LoggerFactory.getLogger(PharmacyConsumer.class);

    private final ConsumptionPostingService consumptionPostingService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the PharmacyConsumer with required dependencies.
     *
     * @param consumptionPostingService service for posting consumption events
     * @param objectMapper              Jackson mapper for JSON deserialization
     */
    public PharmacyConsumer(ConsumptionPostingService consumptionPostingService,
                            ObjectMapper objectMapper) {
        this.consumptionPostingService = consumptionPostingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume pharmacy stock movement requests.
     *
     * <p>Parses the JSON payload, sets up a synthetic trust context,
     * and delegates to {@link ConsumptionPostingService#postPharmacyConsumption}
     * to record the consumption as a ledger event.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "pharmacy.stock.movement.requested", groupId = "inventory-pharmacy-consumer")
    @Transactional
    public void consumePharmacyMovement(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String tenantId = getTextField(event, "tenantId");
            String facilityId = getTextField(event, "facilityId");
            String storeId = getTextField(event, "storeId");
            String itemCode = getTextField(event, "itemCode");
            String batch = getTextField(event, "batch");
            String expiryStr = getTextField(event, "expiry");
            int qty = event.has("qty") ? event.get("qty").asInt(0) : 0;
            String orderId = getTextField(event, "orderId");
            String actorId = getTextField(event, "actorId");

            if (tenantId == null || facilityId == null || storeId == null || itemCode == null) {
                log.warn("Pharmacy stock movement event missing required fields, skipping");
                return;
            }

            if (qty <= 0) {
                log.warn("Pharmacy stock movement event has non-positive qty={}, skipping", qty);
                return;
            }

            // Set up a synthetic TrustContext for the Kafka consumer thread
            TrustContext ctx = new TrustContext(
                    UUID.fromString(tenantId),
                    actorId != null ? actorId : "PHARMACY-SYSTEM",
                    "SYSTEM",
                    "PHARMACY_CONSUMPTION",
                    null,
                    UUID.randomUUID(),
                    UUID.fromString(facilityId),
                    null,
                    null,
                    null);
            TrustContextHolder.set(ctx);

            try {
                LocalDate expiry = expiryStr != null ? LocalDate.parse(expiryStr) : null;

                consumptionPostingService.postPharmacyConsumption(
                        UUID.fromString(facilityId),
                        UUID.fromString(storeId),
                        itemCode,
                        batch,
                        expiry,
                        qty,
                        orderId);

                log.info("Pharmacy consumption posted from Kafka: itemCode={}, qty={}, orderId={}",
                        itemCode, qty, orderId);
            } finally {
                TrustContextHolder.clear();
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse pharmacy stock movement event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing pharmacy stock movement event: {}", e.getMessage(), e);
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
