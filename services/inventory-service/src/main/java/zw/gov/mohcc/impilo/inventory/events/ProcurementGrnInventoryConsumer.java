package zw.gov.mohcc.impilo.inventory.events;

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
import zw.gov.mohcc.impilo.inventory.core.LedgerService;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Consumes {@code procurement.grn.received} to post inventory RECEIPT ledger events.
 */
@Component
@ConditionalOnProperty(prefix = "inventory.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class ProcurementGrnInventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProcurementGrnInventoryConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public ProcurementGrnInventoryConsumer(LedgerService ledgerService, ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "procurement.grn.received", groupId = "inventory-procurement-grn")
    @Transactional
    public void onGrn(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode data = root.has("payload") && root.get("payload").isObject() ? root.get("payload") : root;
            String tenantStr = text(data, "tenantId", "tenant_id");
            String facilityStr = text(data, "facilityId", "facility_id");
            String storeStr = text(data, "storeId", "store_id");
            if (tenantStr == null || facilityStr == null || storeStr == null) {
                log.warn("procurement.grn.received missing tenant/facility/store, skip");
                return;
            }
            UUID facilityId = UUID.fromString(facilityStr);
            UUID storeId = UUID.fromString(storeStr);
            for (JsonNode ln : data.withArray("lines")) {
                String item = text(ln, "itemCode", "item_code");
                int qty = ln.has("quantityReceived") && ln.get("quantityReceived").isNumber()
                        ? ln.get("quantityReceived").intValue()
                        : ln.has("quantityReceived") ? (int) Double.parseDouble(ln.get("quantityReceived").asText()) : 0;
                if (item == null || qty <= 0) {
                    continue;
                }
                String grnLineId = text(ln, "grnLineId", "grn_line_id");
                String idem = "PROC-GRN:" + text(data, "grnId", "grn_id") + ":" + (grnLineId != null ? grnLineId : item);
                TrustContextHolder.set(
                        syntheticContext(tenantStr, facilityStr, text(data, "receivedBy", "received_by")));
                try {
                    LedgerService.LedgerEventData ev = new LedgerService.LedgerEventData(
                            facilityId,
                            storeId,
                            null,
                            item,
                            text(ln, "batchNumber", "batch_number"),
                            parseDate(text(ln, "expiryDate", "expiry_date")),
                            qty,
                            null,
                            LedgerEventType.RECEIPT,
                            "Procurement GRN receipt",
                            "PROCUREMENT_GRN",
                            text(data, "grnId", "grn_id"),
                            idem
                    );
                    ledgerService.postEvent(ev);
                } finally {
                    TrustContextHolder.clear();
                }
            }
        } catch (Exception e) {
            log.error("procurement.grn.received handling failed: {}", e.getMessage(), e);
        }
    }

    private static TrustContext syntheticContext(String tenantIdStr, String facilityIdStr, String actorId) {
        return new TrustContext(
                UUID.fromString(tenantIdStr),
                actorId != null && !actorId.isBlank() ? actorId : "PROCUREMENT-GRN",
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

    private static String text(JsonNode n, String... names) {
        for (String name : names) {
            if (n != null && n.hasNonNull(name)) {
                String s = n.get(name).asText();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
