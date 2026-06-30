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
import zw.gov.mohcc.impilo.assetregistry.core.EquipmentOperationsService;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Updates {@link zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity} projections from IoT heartbeat
 * streams, and derives THRESHOLD_BREACH IoT alerts from the real metric values carried on
 * telemetry.iot.device.reading (event impilo.iot.telemetry.reading.ingested.v1). No telemetry is
 * fabricated — derivation reads only the ingested {@code metric_value}.
 */
@Component
@ConditionalOnProperty(prefix = "asset-registry.supply", name = "kafka-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class AssetEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssetEventConsumer.class);

    private final AssetService assetService;
    private final EquipmentOperationsService equipmentOps;
    private final ObjectMapper objectMapper;

    public AssetEventConsumer(AssetService assetService, EquipmentOperationsService equipmentOps,
                              ObjectMapper objectMapper) {
        this.assetService = assetService;
        this.equipmentOps = equipmentOps;
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
            if (tenantId == null) {
                log.debug("Telemetry event missing tenant_id — skipping");
                return;
            }

            // Heartbeat projection (offline derivation) needs a UUID asset/device id.
            UUID assetId = uuidField(event, "assetId", "asset_id", "deviceId", "device_id");
            if (assetId != null) {
                OffsetDateTime seen = offsetTime(event, "observedAt", "observed_at", "recordedAt", "recorded_at", "timestamp");
                if (seen == null) {
                    seen = OffsetDateTime.now();
                }
                String op = text(event, "operationalStatus", "operational_status", "health", "status");
                assetService.applyDeviceHeartbeat(assetId, tenantId, seen, op);
                log.debug("Applied heartbeat assetId={} tenantId={}", assetId, tenantId);
            }

            // Threshold-breach derivation from a real metric reading. The equipment device link keys
            // on the string device_id (not necessarily a UUID), so use the raw text id here.
            String deviceId = text(event, "deviceId", "device_id");
            String metricType = text(event, "metricType", "metric_type");
            Double metricValue = doubleField(event, "metricValue", "metric_value");
            if (deviceId != null && metricType != null && metricValue != null) {
                equipmentOps.evaluateThresholdReading(tenantId, deviceId, metricType, metricValue);
            }
        } catch (Exception e) {
            log.warn("Failed to process telemetry event: {}", e.getMessage());
        }
    }

    private static Double doubleField(JsonNode node, String... names) {
        for (String n : names) {
            if (node != null && node.hasNonNull(n)) {
                JsonNode v = node.get(n);
                if (v.isNumber()) {
                    return v.asDouble();
                }
                if (v.isTextual() && !v.asText().isBlank()) {
                    try {
                        return Double.parseDouble(v.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // try next
                    }
                }
            }
        }
        return null;
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
