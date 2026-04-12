package zw.gov.mohcc.impilo.pipeline.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pipeline.api.dto.EventEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.IngestResponse;
import zw.gov.mohcc.impilo.pipeline.domain.IngestionStatus;
import zw.gov.mohcc.impilo.pipeline.domain.SourceType;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestionSourceRepository;

/**
 * Bridges canonical Kafka JSON (snake_case wire or nested payload) into the pipeline {@link EventEnvelope}.
 */
@Service
public class BusEventIngestService {

    private static final Logger log = LoggerFactory.getLogger(BusEventIngestService.class);

    private static final UUID NATIONAL_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final IngestionService ingestionService;
    private final IngestionSourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public BusEventIngestService(IngestionService ingestionService,
                                 IngestionSourceRepository sourceRepository,
                                 ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResponse ingestFromKafka(String topic, String sourceId, String rawJson) {
        UUID tenantId = NATIONAL_TENANT;
        ensureSource(tenantId, sourceId);

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            tenantId = parseUuid(text(root, "tenant_id", "tenantId")).orElse(NATIONAL_TENANT);
            ensureSource(tenantId, sourceId);

            EventEnvelope envelope = buildEnvelope(root, sourceId, topic);
            return ingestionService.ingest(envelope, tenantId);
        } catch (Exception e) {
            log.error("Kafka ingest failed topic={} source={}: {}", topic, sourceId, e.getMessage(), e);
            return new IngestResponse("unknown", IngestionStatus.REJECTED.name(), e.getMessage());
        }
    }

    private void ensureSource(UUID tenantId, String sourceId) {
        if (sourceRepository.findByTenantIdAndSourceId(tenantId, sourceId).isPresent()) {
            return;
        }
        IngestionSourceEntity s = new IngestionSourceEntity();
        s.setSourceId(sourceId);
        s.setTenantId(tenantId);
        s.setDisplayName(sourceId + " (auto)");
        s.setDescription("Auto-registered from Kafka topic bridge");
        s.setSourceType(SourceType.SERVICE);
        s.setEnabled(true);
        sourceRepository.save(s);
        log.info("Auto-registered ingestion source {} for tenant {}", sourceId, tenantId);
    }

    private EventEnvelope buildEnvelope(JsonNode root, String sourceId, String topic) {
        JsonNode payloadNode = root.get("payload");
        Map<String, Object> payload;
        if (payloadNode != null && payloadNode.isObject()) {
            payload = objectMapper.convertValue(payloadNode, new TypeReference<>() {});
        } else {
            payload = new HashMap<>();
            payload.put("raw", root.toString());
            payload.put("topic", topic);
        }

        String eventId = text(root, "event_id", "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }

        String tenantStr = text(root, "tenant_id", "tenantId");
        String eventType = text(root, "event_type", "eventType");
        if (eventType == null || eventType.isBlank()) {
            eventType = topic;
        }

        String aggregateType = text(root, "subject_type", "aggregateType", "aggregate_type");
        if (aggregateType == null) {
            aggregateType = "UNKNOWN";
        }
        String aggregateId = text(root, "subject_id", "aggregateId", "aggregate_id");
        if (aggregateId == null) {
            aggregateId = eventId;
        }

        OffsetDateTime occurredAt = parseTime(text(root, "occurred_at", "occurredAt"));
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }

        String podId = text(root, "pod_id", "podId");
        if (podId == null || podId.isBlank()) {
            podId = "national-spine";
        }

        String correlationId = text(root, "correlation_id", "correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String causationId = text(root, "causation_id", "causationId");
        if (causationId == null || causationId.isBlank()) {
            causationId = correlationId;
        }

        String schemaVersion = text(root, "schema_version", "schemaVersion");
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = "1.1";
        }

        return new EventEnvelope(
                eventId,
                tenantStr != null ? tenantStr : NATIONAL_TENANT.toString(),
                sourceId,
                eventType,
                aggregateType,
                aggregateId,
                occurredAt,
                podId,
                correlationId,
                causationId,
                payload,
                schemaVersion
        );
    }

    private static String text(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isNull() && v.isTextual()) {
                return v.asText();
            }
        }
        return null;
    }

    private static java.util.Optional<UUID> parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static OffsetDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
