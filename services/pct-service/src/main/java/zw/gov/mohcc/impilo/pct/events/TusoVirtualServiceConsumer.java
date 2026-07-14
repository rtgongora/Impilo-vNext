package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.core.QueueMaterializationService;

import java.util.UUID;

/**
 * Consumes TUSO virtual-service configuration events
 * ({@code tuso.virtual_service.activated} / {@code .updated} / {@code .suspended}
 * on topic {@code impilo.tuso.virtual_service}) and reconciles the tenant's
 * virtual-pool queues.
 *
 * <p>Same doctrine as {@link PctEventConsumer#consumeTusoWorkspaceUpdated}: the
 * event is a trigger, not the source of truth. PCT reconciles the whole tenant
 * from TUSO's current virtual-service registry, keeping materialisation
 * idempotent and robust to missed/duplicated/replayed events. Handles both the
 * legacy flat payload and the v1.1 envelope (payload nested/stringified).</p>
 */
@Component
public class TusoVirtualServiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(TusoVirtualServiceConsumer.class);

    private final QueueMaterializationService queueMaterializationService;
    private final ObjectMapper objectMapper;

    public TusoVirtualServiceConsumer(QueueMaterializationService queueMaterializationService,
                                      ObjectMapper objectMapper) {
        this.queueMaterializationService = queueMaterializationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "impilo.tuso.virtual_service", groupId = "pct-service")
    public void onVirtualServiceEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = root.has("payload") ? maybeParse(root.get("payload")) : root;
            String tenantId = text(node, "tenantId");
            if (tenantId == null) tenantId = text(root, "tenantId");
            String vsUid = text(node, "vsUid");
            if (tenantId == null) {
                log.warn("TUSO virtual-service event without tenantId — cannot reconcile, skipping: {}", message);
                return;
            }
            log.info("TUSO virtual-service event received (vsUid={}, tenant={}) — reconciling virtual pools",
                    vsUid, tenantId);
            var results = queueMaterializationService.reconcileVirtualPools(UUID.fromString(tenantId));
            log.info("Virtual-pool reconcile after TUSO event: {} entity result(s)", results.size());
        } catch (Exception e) {
            log.error("Failed to process TUSO virtual-service event: {}", e.getMessage(), e);
        }
    }

    private JsonNode maybeParse(JsonNode payload) throws Exception {
        if (payload.isTextual()) return objectMapper.readTree(payload.asText());
        return payload;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
