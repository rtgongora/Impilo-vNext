package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tuso.core.VirtualServiceRegistryService;

import java.util.UUID;

/**
 * Closes the fail-closed activation loop: PCT confirms it has materialised a
 * virtual service's pool queues ({@code pct.telemedicine.pool.materialized})
 * and only THEN does TUSO flip the entity ACTIVATION_REQUESTED → ACTIVE.
 * Stored lifecycle state never overstates runtime reality.
 *
 * <p>Idempotent: replays and confirmations for entities not awaiting
 * activation are no-ops ({@link VirtualServiceRegistryService
 * #markActiveFromMaterialization}).</p>
 */
@Component
public class PctPoolMaterializedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PctPoolMaterializedConsumer.class);

    private final ObjectMapper objectMapper;
    private final VirtualServiceRegistryService registryService;

    public PctPoolMaterializedConsumer(ObjectMapper objectMapper,
                                       VirtualServiceRegistryService registryService) {
        this.objectMapper = objectMapper;
        this.registryService = registryService;
    }

    @KafkaListener(topics = "pct.telemedicine.pool.materialized", groupId = "tuso-service")
    public void onPoolMaterialized(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = root.has("payload") ? maybeParse(root.get("payload")) : root;
            String vsUid = text(node, "vsUid");
            String tenantId = text(node, "tenantId");
            if (vsUid == null || tenantId == null) {
                log.warn("pool.materialized event missing vsUid/tenantId — skipping: {}", message);
                return;
            }
            boolean flipped = registryService.markActiveFromMaterialization(
                    UUID.fromString(tenantId), vsUid, "pct:pool-materialization");
            if (flipped) {
                log.info("Virtual service {} confirmed materialised by PCT — now ACTIVE", vsUid);
            }
        } catch (Exception e) {
            log.error("Failed to process pct.telemedicine.pool.materialized: {}", e.getMessage(), e);
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
