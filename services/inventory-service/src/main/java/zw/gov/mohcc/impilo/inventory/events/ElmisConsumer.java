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
import zw.gov.mohcc.impilo.inventory.core.OnHandService;
import zw.gov.mohcc.impilo.inventory.core.ReconciliationService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;

import java.util.Iterator;
import java.util.UUID;

/**
 * Kafka consumer for eLMIS stock position snapshots.
 *
 * <p>Listens to the {@code elmis.stock.position.snapshot} topic. When an
 * eLMIS snapshot is received, each item position is compared against the
 * internal on-hand balance. Discrepancies are recorded as reconciliation
 * queue entries for operator review.</p>
 *
 * <h3>Expected JSON Payload</h3>
 * <pre>{@code
 * {
 *   "tenantId": "uuid",
 *   "facilityId": "uuid",
 *   "storeId": "uuid",
 *   "snapshotDate": "2026-02-07",
 *   "items": [
 *     {
 *       "itemCode": "PARA-500",
 *       "batch": "BATCH-001",
 *       "externalQty": 100
 *     }
 *   ]
 * }
 * }</pre>
 */
@Service
@ConditionalOnProperty(prefix = "inventory.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class ElmisConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElmisConsumer.class);

    private final OnHandService onHandService;
    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the ElmisConsumer with required dependencies.
     *
     * @param onHandService          service for querying internal on-hand positions
     * @param reconciliationService  service for creating reconciliation entries
     * @param objectMapper           Jackson mapper for JSON deserialization
     */
    public ElmisConsumer(OnHandService onHandService,
                         ReconciliationService reconciliationService,
                         ObjectMapper objectMapper) {
        this.onHandService = onHandService;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume eLMIS stock position snapshot events.
     *
     * <p>For each item in the snapshot, looks up the internal on-hand quantity
     * and compares it with the external quantity. If they differ, a reconciliation
     * queue entry is created for operator review.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "elmis.stock.position.snapshot", groupId = "inventory-elmis-consumer")
    @Transactional
    public void consumeElmisSnapshot(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String tenantId = getTextField(event, "tenantId");
            String facilityId = getTextField(event, "facilityId");
            String storeId = getTextField(event, "storeId");

            if (tenantId == null || facilityId == null || storeId == null) {
                log.warn("eLMIS snapshot event missing required fields (tenantId, facilityId, storeId), skipping");
                return;
            }

            UUID facilityUuid = UUID.fromString(facilityId);
            UUID storeUuid = UUID.fromString(storeId);

            JsonNode itemsNode = event.get("items");
            if (itemsNode == null || !itemsNode.isArray()) {
                log.warn("eLMIS snapshot event has no items array, skipping");
                return;
            }

            int reconcileCount = 0;
            Iterator<JsonNode> itemIterator = itemsNode.elements();
            while (itemIterator.hasNext()) {
                JsonNode itemNode = itemIterator.next();

                String itemCode = getTextField(itemNode, "itemCode");
                String batch = getTextField(itemNode, "batch");
                int externalQty = itemNode.has("externalQty") ? itemNode.get("externalQty").asInt(0) : 0;

                if (itemCode == null) {
                    log.debug("Skipping eLMIS snapshot item with no itemCode");
                    continue;
                }

                // Look up internal on-hand quantity
                int internalQty = 0;
                try {
                    OnHandEntity position = onHandService.getPosition(
                            facilityUuid, storeUuid, itemCode, batch, null);
                    if (position != null) {
                        internalQty = position.getQtyOnHand();
                    }
                } catch (Exception e) {
                    log.debug("No internal position found for itemCode={}, batch={}", itemCode, batch);
                }

                // Compare and create reconciliation entry if discrepancy exists
                if (internalQty != externalQty) {
                    reconciliationService.createEntry(
                            facilityUuid, storeUuid, itemCode, batch,
                            internalQty, externalQty);
                    reconcileCount++;

                    log.debug("Reconciliation entry created: itemCode={}, batch={}, internal={}, external={}",
                            itemCode, batch, internalQty, externalQty);
                }
            }

            if (reconcileCount > 0) {
                log.info("eLMIS snapshot processed: facilityId={}, discrepancies={}",
                        facilityId, reconcileCount);
            } else {
                log.info("eLMIS snapshot processed: facilityId={}, all quantities match", facilityId);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse eLMIS snapshot event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing eLMIS snapshot event: {}", e.getMessage(), e);
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
