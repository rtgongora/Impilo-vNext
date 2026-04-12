package zw.gov.mohcc.impilo.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.inventory.core.LedgerService;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumes pharmacy supply-chain topics to keep bin stock aligned with dispensing and movements.
 */
@Component
@ConditionalOnProperty(prefix = "inventory.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ConsumptionPostingService consumptionPostingService;
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(ConsumptionPostingService consumptionPostingService,
                                  LedgerService ledgerService,
                                  ObjectMapper objectMapper) {
        this.consumptionPostingService = consumptionPostingService;
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"pharmacy.dispense.complete", "pharmacy.stock.movement.recorded"},
            groupId = "inventory-supply-plane")
    @Transactional
    public void onPharmacySupplyEvent(String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode event = objectMapper.readTree(message);
            switch (topic) {
                case "pharmacy.dispense.complete" -> handleDispenseComplete(event);
                case "pharmacy.stock.movement.recorded" -> handleStockMovementRecorded(event);
                default -> log.debug("Ignoring unexpected topic {}", topic);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse pharmacy supply event on {}: {}", topic, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error handling pharmacy supply event on {}: {}", topic, e.getMessage(), e);
        }
    }

    private void handleDispenseComplete(JsonNode event) {
        String tenantIdStr = text(event, "tenantId", "tenant_id");
        String facilityIdStr = text(event, "facilityId", "facility_id");
        String storeIdStr = text(event, "storeId", "store_id");
        String itemCode = text(event, "itemCode", "item_code");
        String batch = text(event, "batch", "batchNo", "batch_no");
        String expiryStr = text(event, "expiry", "expiryDate", "expiry_date");
        int qty = intField(event, "qty", "quantity", "dispensedQty", "dispensed_qty");
        String orderKey = text(event, "orderId", "order_id", "dispenseId", "dispense_id", "prescriptionId", "prescription_id");

        if (tenantIdStr == null || facilityIdStr == null || storeIdStr == null || itemCode == null) {
            log.warn("pharmacy.dispense.complete missing tenant/facility/store/item, skipping");
            return;
        }
        if (qty <= 0) {
            log.warn("pharmacy.dispense.complete non-positive qty, skipping");
            return;
        }
        if (orderKey == null || orderKey.isBlank()) {
            log.warn("pharmacy.dispense.complete missing order/dispense id, skipping");
            return;
        }

        TrustContext ctx = syntheticContext(tenantIdStr, facilityIdStr, text(event, "actorId", "actor_id"));
        TrustContextHolder.set(ctx);
        try {
            LocalDate expiry = expiryStr != null && !expiryStr.isBlank() ? LocalDate.parse(expiryStr) : null;
            LedgerEventEntity posted = consumptionPostingService.postPharmacyConsumption(
                    UUID.fromString(facilityIdStr),
                    UUID.fromString(storeIdStr),
                    itemCode,
                    batch,
                    expiry,
                    qty,
                    orderKey);
            log.info("Dispense complete deducted stock eventId={} item={} qty={}", posted.getEventId(), itemCode, qty);
        } finally {
            TrustContextHolder.clear();
        }
    }

    private void handleStockMovementRecorded(JsonNode event) {
        String tenantIdStr = text(event, "tenantId", "tenant_id");
        String facilityIdStr = text(event, "facilityId", "facility_id");
        String storeIdStr = text(event, "storeId", "store_id");
        String itemCode = text(event, "itemCode", "item_code");
        String batch = text(event, "batch", "batch_no");
        String expiryStr = text(event, "expiry", "expiry_date");
        String binIdStr = text(event, "binId", "bin_id");
        int qtyDelta = intField(event, "qtyDelta", "qty_delta", "quantityDelta", "quantity_delta", "quantity");
        String movementId = text(event, "movementId", "movement_id", "id");

        if (tenantIdStr == null || facilityIdStr == null || storeIdStr == null || itemCode == null) {
            log.warn("pharmacy.stock.movement.recorded missing tenant/facility/store/item, skipping");
            return;
        }
        if (qtyDelta == 0) {
            log.warn("pharmacy.stock.movement.recorded qty delta is zero, skipping");
            return;
        }
        if (movementId == null || movementId.isBlank()) {
            movementId = "UNKNOWN-" + UUID.randomUUID();
        }

        UUID binId = binIdStr != null && !binIdStr.isBlank() ? UUID.fromString(binIdStr) : null;
        LocalDate expiry = expiryStr != null && !expiryStr.isBlank() ? LocalDate.parse(expiryStr) : null;

        TrustContext ctx = syntheticContext(tenantIdStr, facilityIdStr, text(event, "actorId", "actor_id"));
        TrustContextHolder.set(ctx);
        try {
            LedgerService.LedgerEventData data = new LedgerService.LedgerEventData(
                    UUID.fromString(facilityIdStr),
                    UUID.fromString(storeIdStr),
                    binId,
                    itemCode,
                    batch,
                    expiry,
                    qtyDelta,
                    text(event, "uom"),
                    LedgerEventType.ADJUSTMENT,
                    "Pharmacy stock movement recorded",
                    "PHARMACY_MOVEMENT",
                    movementId,
                    "PHARMACY_MOVEMENT:" + movementId + ":" + itemCode + ":" + qtyDelta
            );
            LedgerEventEntity posted = ledgerService.postEvent(data);
            log.info("Stock movement recorded ledger eventId={} item={} qtyDelta={}", posted.getEventId(), itemCode, qtyDelta);
        } finally {
            TrustContextHolder.clear();
        }
    }

    private static TrustContext syntheticContext(String tenantIdStr, String facilityIdStr, String actorId) {
        return new TrustContext(
                UUID.fromString(tenantIdStr),
                actorId != null && !actorId.isBlank() ? actorId : "PHARMACY-SUPPLY-EVENT",
                "SYSTEM",
                "SUPPLY_PLANE",
                null,
                UUID.randomUUID(),
                UUID.fromString(facilityIdStr),
                null,
                null,
                AccessMode.INTERNAL
        );
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }

    private static int intField(JsonNode node, String... names) {
        for (String n : names) {
            if (node.has(n) && !node.get(n).isNull()) {
                JsonNode v = node.get(n);
                if (v.isNumber()) {
                    return v.intValue();
                }
                try {
                    return Integer.parseInt(v.asText().trim());
                } catch (NumberFormatException ignored) {
                    // try next alias
                }
            }
        }
        return 0;
    }
}
