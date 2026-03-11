package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.api.dto.IngestEventRequest;
import zw.gov.mohcc.impilo.ndr.api.dto.IngestEventResponse;
import zw.gov.mohcc.impilo.ndr.domain.BronzeEventEntity;
import zw.gov.mohcc.impilo.ndr.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.ndr.repository.BronzeEventRepository;
import zw.gov.mohcc.impilo.ndr.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NdrIngestService {

    private static final Logger log = LoggerFactory.getLogger(NdrIngestService.class);
    private static final String PRODUCER = "ndr-service";

    private final BronzeEventRepository bronzeRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NdrIngestService(BronzeEventRepository bronzeRepository,
                            OutboxEventRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.bronzeRepository = bronzeRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(IngestEventRequest request) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "event_id is required");
        }
        if (request.schemaVersion() == null || request.schemaVersion() < 1) {
            return new ValidationResult("SCHEMA_VALIDATION_FAILED",
                    "schema_version missing or < 1");
        }
        if (request.eventType() == null || request.eventType().isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "event_type is required");
        }
        if (!request.eventType().startsWith("impilo.")) {
            return new ValidationResult("INVALID_EVENT_TYPE",
                    "event_type must start with 'impilo.'");
        }
        if (!request.eventType().endsWith(".v1")) {
            return new ValidationResult("INVALID_EVENT_TYPE",
                    "event_type must end with '.v1'");
        }
        String partitionKey = resolvePartitionKey(request);
        if (partitionKey == null || partitionKey.isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "meta.partition_key is required");
        }
        if (request.subjectId() == null || request.subjectId().isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "subject_id is required");
        }
        if (request.subjectType() == null || request.subjectType().isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "subject_type is required");
        }
        if (request.occurredAt() == null || request.occurredAt().isBlank()) {
            return new ValidationResult("INVALID_ENVELOPE", "occurred_at is required");
        }
        return null;
    }

    @Transactional
    public IngestEventResponse ingestEvent(IngestEventRequest request, String rawJson,
                                           UUID tenantId, String podId,
                                           String requestId, String correlationId,
                                           String idempotencyKey) {
        Optional<BronzeEventEntity> existingByEventId = bronzeRepository
                .findByTenantIdAndPodIdAndEventId(tenantId, podId, request.eventId());

        if (existingByEventId.isPresent()) {
            BronzeEventEntity existing = existingByEventId.get();
            return new IngestEventResponse(
                    existing.getReceiptId().toString(), existing.getEventId(),
                    existing.getStoredAt().toString(), "REPLAY");
        }

        Optional<BronzeEventEntity> existingByIdemKey = bronzeRepository
                .findByTenantIdAndPodIdAndIdempotencyKey(tenantId, podId, idempotencyKey);

        if (existingByIdemKey.isPresent()) {
            BronzeEventEntity existing = existingByIdemKey.get();
            return new IngestEventResponse(
                    existing.getReceiptId().toString(), existing.getEventId(),
                    existing.getStoredAt().toString(), "REPLAY");
        }

        String partitionKey = resolvePartitionKey(request);
        OffsetDateTime occurredAt = OffsetDateTime.parse(request.occurredAt());
        OffsetDateTime emittedAt = request.emittedAt() != null
                ? OffsetDateTime.parse(request.emittedAt()) : null;

        BronzeEventEntity bronze = new BronzeEventEntity();
        bronze.setTenantId(tenantId);
        bronze.setPodId(podId);
        bronze.setRequestId(requestId);
        bronze.setCorrelationId(correlationId);
        bronze.setIdempotencyKey(idempotencyKey);
        bronze.setEventId(request.eventId());
        bronze.setEventType(request.eventType());
        bronze.setSchemaVersion(request.schemaVersion());
        bronze.setOccurredAt(occurredAt);
        bronze.setEmittedAt(emittedAt);
        bronze.setSubjectType(request.subjectType());
        bronze.setSubjectId(request.subjectId());
        bronze.setPartitionKey(partitionKey);
        bronze.setEnvelopeJson(rawJson);
        bronzeRepository.save(bronze);

        appendOutboxEvent(bronze, tenantId, podId, correlationId, idempotencyKey, partitionKey);

        log.info("NDR ingested bronze event [receiptId={}, eventId={}]",
                bronze.getReceiptId(), request.eventId());

        return new IngestEventResponse(
                bronze.getReceiptId().toString(), bronze.getEventId(),
                bronze.getStoredAt().toString(), "NEW");
    }

    private void appendOutboxEvent(BronzeEventEntity bronze, UUID tenantId, String podId,
                                    String correlationId, String idempotencyKey,
                                    String partitionKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("op", "CREATE");
        payload.put("after", Map.of(
                "receipt_id", bronze.getReceiptId().toString(),
                "event_id", bronze.getEventId(),
                "event_type", bronze.getEventType(),
                "subject_type", bronze.getSubjectType(),
                "subject_id", bronze.getSubjectId(),
                "partition_key", partitionKey,
                "stored_at", bronze.getStoredAt().toString()));
        payload.put("changed_fields", List.of(
                "receipt_id", "event_id", "event_type", "subject_type",
                "subject_id", "partition_key", "envelope_json", "stored_at"));

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("BronzeEvent");
        outbox.setAggregateId(bronze.getReceiptId().toString());
        outbox.setEventType("impilo.ndr.bronze.received.v1");
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(bronze.getSubjectId());
        outbox.setSubjectType(bronze.getSubjectType());
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String resolvePartitionKey(IngestEventRequest request) {
        if (request.meta() != null) {
            Object pk = request.meta().get("partition_key");
            if (pk != null && !pk.toString().isBlank()) return pk.toString();
        }
        if (request.subjectId() != null && !request.subjectId().isBlank()) return request.subjectId();
        if (request.eventId() != null && !request.eventId().isBlank()) return request.eventId();
        return null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }

    public record ValidationResult(String code, String message) {}
}
