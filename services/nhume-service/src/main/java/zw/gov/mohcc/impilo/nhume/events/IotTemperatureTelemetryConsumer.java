package zw.gov.mohcc.impilo.nhume.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.nhume.core.NhumeDeliveryService;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryRequestRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * OF-B19 §12.6 — cold-chain telemetry binding. Consumes the iot-ingestion bus
 * ({@code telemetry.iot.device.reading}, String-serialised JSON per the iot
 * outbox contract: {@code reading_id, tenant_id, device_id, metric_type,
 * metric_value, unit, recorded_at, source}), keeps ONLY temperature metrics,
 * and fans each reading into the in-transit cold-chain deliveries whose sealed
 * sensor ({@code sensor_device_ref}) is that device.
 *
 * <p>Every accepted reading becomes a TEMPERATURE_READING custody event; band
 * breaches ride the unsuppressible excursion path in
 * {@link NhumeDeliveryService#recordTemperatureReading} — this consumer has no
 * ability to suppress, sample or debounce a breach (T24 counter-control).</p>
 *
 * <p><b>Malformed-payload tolerance:</b> unparseable or field-incomplete
 * messages are logged and skipped, never rethrown — iot-ingestion's DLQ keeps
 * the upstream evidence, and poisoning the consumer group would silence the
 * whole cold-chain lane.</p>
 *
 * <p><b>Deploy note (preview/prod):</b> nhume previously had no Kafka consumer;
 * the deployment env must now inject {@code KAFKA_BOOTSTRAP_SERVERS} (see
 * application.yml) and may gate startup with
 * {@code NHUME_COLDCHAIN_TELEMETRY_ENABLED=false} where no broker exists.</p>
 */
@Service
@Profile("!test")
public class IotTemperatureTelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(IotTemperatureTelemetryConsumer.class);

    private final DeliveryRequestRepository deliveryRepo;
    private final NhumeDeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public IotTemperatureTelemetryConsumer(DeliveryRequestRepository deliveryRepo,
                                           NhumeDeliveryService deliveryService,
                                           ObjectMapper objectMapper) {
        this.deliveryRepo = deliveryRepo;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "telemetry.iot.device.reading", groupId = "nhume-service",
            autoStartup = "${nhume.coldchain.telemetry-enabled:true}")
    public void consume(String message) {
        TemperatureReading reading = parse(message);
        if (reading == null) {
            return; // reason already logged; tolerated
        }
        List<DeliveryRequestEntity> candidates =
                deliveryRepo.findActiveColdChainBySensor(reading.deviceId());
        if (candidates.isEmpty()) {
            return; // sensor not bound to any in-transit cold delivery — not ours
        }
        for (DeliveryRequestEntity d : candidates) {
            if (reading.tenantId() != null && !reading.tenantId().equals(d.getTenantId())) {
                log.warn("Cold-chain telemetry tenant mismatch: sensor {} reading tenant {} vs "
                        + "delivery {} tenant {} — skipped", reading.deviceId(), reading.tenantId(),
                        d.getDeliveryId(), d.getTenantId());
                continue;
            }
            try {
                deliveryService.recordTemperatureReading(d.getDeliveryId(), reading.deviceId(),
                        reading.value(), reading.recordedAt(), "IOT_BUS", null);
            } catch (Exception e) {
                // Loud but non-fatal: one broken delivery row must not starve the
                // rest of the cold-chain lane of readings.
                log.error("Failed to record cold-chain reading for delivery {} (sensor {}): {}",
                        d.getDeliveryId(), reading.deviceId(), e.getMessage(), e);
            }
        }
    }

    record TemperatureReading(UUID tenantId, String deviceId, BigDecimal value,
                              OffsetDateTime recordedAt) {}

    /** Null when unusable or not a temperature metric; the reason is logged. Package-visible for tests. */
    TemperatureReading parse(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (Exception e) {
            log.error("Unparseable iot telemetry message ({}): {}", e.getMessage(), truncate(message));
            return null;
        }
        if (event == null || !event.isObject()) {
            log.error("Iot telemetry message is not a JSON object: {}", truncate(message));
            return null;
        }
        String metricType = text(event, "metric_type");
        if (metricType == null
                || !metricType.toUpperCase(Locale.ROOT).contains("TEMP")) {
            return null; // other metric classes are not the cold-chain lane's business
        }
        String deviceId = text(event, "device_id");
        String metricValue = text(event, "metric_value");
        if (deviceId == null || metricValue == null) {
            log.error("Iot temperature message missing device_id/metric_value: {}", truncate(message));
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(metricValue.trim());
        } catch (NumberFormatException e) {
            log.error("Iot temperature message carries non-numeric metric_value '{}' (device={})",
                    metricValue, deviceId);
            return null;
        }
        OffsetDateTime recordedAt = null;
        String recordedAtRaw = text(event, "recorded_at");
        if (recordedAtRaw != null) {
            try {
                recordedAt = OffsetDateTime.parse(recordedAtRaw);
            } catch (Exception e) {
                log.warn("Iot temperature message carries unparseable recorded_at '{}' (device={})"
                        + " — proceeding with server time", recordedAtRaw, deviceId);
            }
        }
        UUID tenantId = null;
        String tenantRaw = text(event, "tenant_id");
        if (tenantRaw != null) {
            try {
                tenantId = UUID.fromString(tenantRaw);
            } catch (IllegalArgumentException e) {
                log.warn("Iot temperature message carries non-UUID tenant_id '{}' (device={})",
                        tenantRaw, deviceId);
            }
        }
        return new TemperatureReading(tenantId, deviceId, value, recordedAt);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
