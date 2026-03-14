package zw.gov.mohcc.impilo.iotingestion.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.iotingestion.api.dto.IngestTelemetryRequest;
import zw.gov.mohcc.impilo.iotingestion.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryDlqEntity;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.iotingestion.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.iotingestion.repository.TelemetryDlqRepository;
import zw.gov.mohcc.impilo.iotingestion.repository.TelemetryReadingRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);
    private static final String AGGREGATE_TYPE = "TelemetryReading";
    private static final String PRODUCER = "iot-ingestion-service";
    private static final String SUBJECT_TYPE = "Device";
    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1", "2");

    private final TelemetryReadingRepository readingRepository;
    private final TelemetryDlqRepository dlqRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TelemetryIngestionService(TelemetryReadingRepository readingRepository,
                                      TelemetryDlqRepository dlqRepository,
                                      OutboxEventRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.readingRepository = readingRepository;
        this.dlqRepository = dlqRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResult ingestReading(UUID tenantId, String podId, String correlationId,
                                      IngestTelemetryRequest request, String source) {
        // Validate schema_version
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(request.schemaVersion())) {
            String rawPayload = toJson(request);
            TelemetryDlqEntity dlq = new TelemetryDlqEntity(
                    request.deviceId(), tenantId, rawPayload,
                    "INVALID_SCHEMA_VERSION",
                    "Unsupported schema_version: " + request.schemaVersion()
                            + ". Supported: " + SUPPORTED_SCHEMA_VERSIONS,
                    source, request.schemaVersion());
            dlqRepository.save(dlq);
            log.warn("DLQ: invalid schema_version={} for device={}", request.schemaVersion(), request.deviceId());
            return new IngestResult(null, true, dlq.getDlqId());
        }

        UUID readingId = UUID.randomUUID();
        OffsetDateTime recordedAt = OffsetDateTime.parse(request.recordedAt());

        TelemetryReadingEntity reading = new TelemetryReadingEntity(
                readingId, request.deviceId(), tenantId,
                request.metricType(), request.metricValue(), request.unit(),
                request.schemaVersion(), recordedAt, source);
        reading.setMetadataJson(toJson(request.metadata()));

        readingRepository.save(reading);

        // Emit telemetry event on SEPARATE telemetry bus
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reading_id", readingId.toString());
        payload.put("device_id", request.deviceId());
        payload.put("metric_type", request.metricType());
        payload.put("metric_value", request.metricValue());
        payload.put("unit", request.unit());
        payload.put("recorded_at", request.recordedAt());
        payload.put("source", source);

        appendOutboxEvent(
                "impilo.iot.telemetry.reading.ingested.v1",
                readingId.toString(), tenantId, podId, correlationId,
                null, payload, request.deviceId());

        log.info("Ingested telemetry [readingId={}, device={}, metric={}]",
                readingId, request.deviceId(), request.metricType());
        return new IngestResult(readingId, false, null);
    }

    @Transactional
    public BatchIngestResult ingestBatch(UUID tenantId, String podId, String correlationId,
                                          List<IngestTelemetryRequest> readings, String source) {
        int accepted = 0;
        int rejected = 0;
        List<UUID> readingIds = new ArrayList<>();

        for (IngestTelemetryRequest request : readings) {
            IngestResult result = ingestReading(tenantId, podId, correlationId, request, source);
            if (result.rejected()) {
                rejected++;
            } else {
                accepted++;
                readingIds.add(result.readingId());
            }
        }

        log.info("Batch ingest complete: accepted={}, rejected={}", accepted, rejected);
        return new BatchIngestResult(accepted, rejected, readingIds);
    }

    @Transactional(readOnly = true)
    public Page<TelemetryReadingEntity> listReadings(UUID tenantId, String deviceId,
                                                       String metricType, int page, int size) {
        return readingRepository.findFiltered(tenantId, deviceId, metricType, PageRequest.of(page, size));
    }

    private void appendOutboxEvent(String eventType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(partitionKey);
        outbox.setSubjectType(SUBJECT_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public record IngestResult(UUID readingId, boolean rejected, UUID dlqId) {}
    public record BatchIngestResult(int accepted, int rejected, List<UUID> readingIds) {}
}
