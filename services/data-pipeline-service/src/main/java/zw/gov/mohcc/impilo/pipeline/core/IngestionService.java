package zw.gov.mohcc.impilo.pipeline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pipeline.api.dto.EventEnvelope;
import zw.gov.mohcc.impilo.pipeline.api.dto.IngestResponse;
import zw.gov.mohcc.impilo.pipeline.domain.IngestionStatus;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestedEventEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestedEventRepository;
import zw.gov.mohcc.impilo.pipeline.persistence.repository.IngestionSourceRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Core ingestion engine that accepts v1.1 EventEnvelope payloads,
 * persists curated pipeline records, advances watermarks, and emits
 * outbox events for downstream consumers.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestedEventRepository eventRepository;
    private final IngestionSourceRepository sourceRepository;
    private final EventOutboxRepository outboxRepository;
    private final WatermarkService watermarkService;
    private final ObjectMapper objectMapper;

    public IngestionService(IngestedEventRepository eventRepository,
                            IngestionSourceRepository sourceRepository,
                            EventOutboxRepository outboxRepository,
                            WatermarkService watermarkService,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.sourceRepository = sourceRepository;
        this.outboxRepository = outboxRepository;
        this.watermarkService = watermarkService;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingest a v1.1 EventEnvelope. Validates the source, deduplicates by eventId,
     * persists the event, advances the watermark, and writes an outbox event.
     *
     * @param envelope the event envelope to ingest
     * @param tenantId the tenant UUID from the request context
     * @return an IngestResponse indicating the result
     */
    @Transactional
    public IngestResponse ingest(EventEnvelope envelope, UUID tenantId) {
        // Duplicate check
        if (eventRepository.existsByEventId(envelope.eventId())) {
            log.info("Duplicate event ignored: {}", envelope.eventId());
            return new IngestResponse(envelope.eventId(), IngestionStatus.DUPLICATE.name(),
                    "Event already ingested");
        }

        // Validate source exists and is enabled
        IngestionSourceEntity source = sourceRepository
                .findByTenantIdAndSourceId(tenantId, envelope.sourceId())
                .orElse(null);

        if (source == null || !source.isEnabled()) {
            log.warn("Rejected event {} from unknown/disabled source: {}",
                    envelope.eventId(), envelope.sourceId());
            return new IngestResponse(envelope.eventId(), IngestionStatus.REJECTED.name(),
                    "Unknown or disabled source: " + envelope.sourceId());
        }

        // Persist ingested event
        IngestedEventEntity event = new IngestedEventEntity();
        event.setEventId(envelope.eventId());
        event.setTenantId(tenantId);
        event.setSourceId(envelope.sourceId());
        event.setEventType(envelope.eventType());
        event.setAggregateType(envelope.aggregateType());
        event.setAggregateId(envelope.aggregateId());
        event.setOccurredAt(envelope.occurredAt());
        event.setPodId(envelope.podId());
        event.setCorrelationId(envelope.correlationId());
        event.setCausationId(envelope.causationId());
        event.setPayload(serializePayload(envelope.payload()));
        event.setSchemaVersion(envelope.schemaVersion() != null ? envelope.schemaVersion() : "1.1");
        event.setIngestionStatus(IngestionStatus.ACCEPTED);

        eventRepository.save(event);

        // Advance watermark
        watermarkService.advance(tenantId, envelope.sourceId(),
                envelope.eventId(), envelope.occurredAt());

        // Write outbox event
        writeOutboxEvent(tenantId, envelope);

        log.info("Ingested event {} from source {} (type={})",
                envelope.eventId(), envelope.sourceId(), envelope.eventType());

        return new IngestResponse(envelope.eventId(), IngestionStatus.ACCEPTED.name(),
                "Event ingested successfully");
    }

    private void writeOutboxEvent(UUID tenantId, EventEnvelope envelope) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("IngestedEvent");
        outbox.setAggregateId(envelope.eventId());
        outbox.setEventType("EVENT_INGESTED");
        outbox.setTenantId(tenantId);

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", envelope.eventId(),
                    "sourceId", envelope.sourceId(),
                    "eventType", envelope.eventType(),
                    "aggregateType", envelope.aggregateType() != null ? envelope.aggregateType() : "",
                    "aggregateId", envelope.aggregateId() != null ? envelope.aggregateId() : "",
                    "occurredAt", envelope.occurredAt().toString(),
                    "tenantId", tenantId.toString()
            ));
            outbox.setPayload(payload);
        } catch (JsonProcessingException e) {
            outbox.setPayload("{}");
            log.error("Failed to serialize outbox payload for event {}", envelope.eventId(), e);
        }

        outboxRepository.save(outbox);
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload", e);
            return "{}";
        }
    }
}
