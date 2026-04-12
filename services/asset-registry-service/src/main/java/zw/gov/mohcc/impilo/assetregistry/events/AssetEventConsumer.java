package zw.gov.mohcc.impilo.assetregistry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.assetregistry.core.AssetService;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Updates {@link zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity} projections from IoT heartbeat streams.
 */
@Component
@ConditionalOnProperty(prefix = "asset-registry.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class AssetEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssetEventConsumer.class);

    private final AssetService assetService;
    private final ObjectMapper objectMapper;

    public AssetEventConsumer(AssetService assetService, ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"telemetry.iot.device.reading", "telemetry.tuso.device.heartbeat"},
            groupId = "asset-registry-telemetry")
    @Transactional
    public void onTelemetryHeartbeat(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode event = root.has("payload") ? root.get("payload") : root;

            UUID tenantId = uuidField(event, "tenantId", "tenant_id");
            UUID assetId = uuidField(event, "assetId", "asset_id", "deviceId", "device_id");
            if (tenantId == null || assetId == null) {
                log.debug("Telemetry heartbeat missing tenant_id or asset/device id — skipping");
                return;
            }

            OffsetDateTime seen = offsetTime(event, "observedAt", "observed_at", "recordedAt", "recorded_at", "timestamp");
            if (seen == null) {
                seen = OffsetDateTime.now();
            }
            String op = text(event, "operationalStatus", "operational_status", "health", "status");
            assetService.applyDeviceHeartbeat(assetId, tenantId, seen, op);
            log.debug("Applied heartbeat assetId={} tenantId={}", assetId, tenantId);
        } catch (Exception e) {
            log.warn("Failed to process telemetry heartbeat: {}", e.getMessage());
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            if (node != null && node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }

    private static UUID uuidField(JsonNode node, String... names) {
        for (String n : names) {
            if (node != null && node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                try {
                    return UUID.fromString(node.get(n).asText().trim());
                } catch (IllegalArgumentException ignored) {
                    // try next
                }
            }
        }
        return null;
    }

    private static OffsetDateTime offsetTime(JsonNode node, String... names) {
        for (String n : names) {
            if (node != null && node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                try {
                    return OffsetDateTime.parse(node.get(n).asText());
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
