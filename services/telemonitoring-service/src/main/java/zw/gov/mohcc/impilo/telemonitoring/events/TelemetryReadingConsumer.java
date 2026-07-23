package zw.gov.mohcc.impilo.telemonitoring.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.telemonitoring.core.ReadingIngestionService;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * §14.5 step 11 — the telemetry-bus consumer (OF-B25). Binds to
 * {@code telemetry.iot.device.reading}: the topic iot-ingestion's outbox publisher
 * routes {@code impilo.iot.telemetry.reading.ingested.v1} events onto (see
 * {@code IotOutboxPublisher.routeTelemetryTopic}). Payload contract (String-serialised
 * JSON, per the iot outbox): {@code reading_id, tenant_id, device_id, metric_type,
 * metric_value, unit, recorded_at, source} — plus an optional {@code patient_cpid} /
 * {@code subject_cpid} claim, which the assignment gate cross-checks (journey #62).
 *
 * <p><b>Malformed-payload tolerance:</b> a message that cannot be parsed or lacks the
 * fields needed to even form a reading identity (device, metric, timestamp) is logged
 * and counted, never thrown — iot-ingestion's DLQ (step 6) already retains the
 * upstream evidence, and poisoning the consumer group would silence the whole bus.</p>
 */
@Service
@Profile("!test")
public class TelemetryReadingConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryReadingConsumer.class);

    private final ReadingIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public TelemetryReadingConsumer(ReadingIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "telemetry.iot.device.reading", groupId = "telemonitoring-service")
    public void consume(String message) {
        ReadingIngestionService.IngestCommand cmd = parse(message);
        if (cmd == null) {
            return; // logged inside parse — tolerated, never poison-pills the group
        }
        TrustContext ctx = new TrustContext(
                cmd.tenantId(),
                "IOT-TELEMETRY",
                "SYSTEM",
                "TREATMENT",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null);
        TrustContextHolder.set(ctx);
        try {
            ingestionService.ingest(cmd);
        } catch (Exception e) {
            // Unexpected processing failure: surfaced loudly but not rethrown — a single
            // bad reading must not halt clinical consumption for every device.
            log.error("Failed to ingest telemetry reading (device={}, metric={}): {}",
                    cmd.deviceRef(), cmd.metricCode(), e.getMessage(), e);
        } finally {
            TrustContextHolder.clear();
        }
    }

    /** Null when the message is unusable; the reason is logged. Package-visible for tests. */
    ReadingIngestionService.IngestCommand parse(String message) {
        JsonNode event;
        try {
            event = objectMapper.readTree(message);
        } catch (Exception e) {
            log.error("Unparseable telemetry bus message ({}): {}", e.getMessage(), truncate(message));
            return null;
        }
        if (event == null || !event.isObject()) {
            log.error("Telemetry bus message is not a JSON object: {}", truncate(message));
            return null;
        }
        String deviceId = text(event, "device_id");
        String metricType = text(event, "metric_type");
        String recordedAt = text(event, "recorded_at");
        String tenantId = text(event, "tenant_id");
        if (deviceId == null || metricType == null || recordedAt == null || tenantId == null) {
            log.error("Telemetry bus message missing required fields (device_id/metric_type/recorded_at/tenant_id): {}",
                    truncate(message));
            return null;
        }
        OffsetDateTime measuredAt;
        try {
            measuredAt = OffsetDateTime.parse(recordedAt);
        } catch (Exception e) {
            log.error("Telemetry bus message carries unparseable recorded_at '{}' (device={})", recordedAt, deviceId);
            return null;
        }
        UUID tenant;
        try {
            tenant = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            log.error("Telemetry bus message carries non-UUID tenant_id '{}' (device={})", tenantId, deviceId);
            return null;
        }
        String claimedSubject = text(event, "patient_cpid");
        if (claimedSubject == null) {
            claimedSubject = text(event, "subject_cpid");
        }
        return new ReadingIngestionService.IngestCommand(
                tenant,
                text(event, "reading_id"),
                deviceId,
                metricType,
                text(event, "metric_value"),
                text(event, "unit"),
                measuredAt,
                text(event, "source"),
                claimedSubject);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }

    private static String truncate(String message) {
        if (message == null) {
            return "<null>";
        }
        return message.length() <= 300 ? message : message.substring(0, 300) + "…";
    }
}
