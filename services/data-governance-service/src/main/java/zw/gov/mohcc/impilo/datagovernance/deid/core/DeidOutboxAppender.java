package zw.gov.mohcc.impilo.datagovernance.deid.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional-outbox appender for the de-identification sub-domain. Mirrors
 * the outbox contract used by {@code GovernanceService} /
 * {@code PrivacyOutboxAppender} so every dataset registration, policy upsert
 * and run state change reliably emits a domain event. Payloads carry counts,
 * codes and identifiers only — never row values.
 */
@Component
public class DeidOutboxAppender {

    private static final String PRODUCER = "data-governance-service";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DeidOutboxAppender(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void append(String eventType, String aggregateType, String aggregateId,
                       UUID tenantId, String podId, String correlationId,
                       String idempotencyKey, Map<String, Object> payload,
                       String partitionKey, String subjectId, String subjectType) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        UUID correlationUuid = correlationUuidOrDerived(correlationId);
        outbox.setCorrelationId(correlationUuid);
        outbox.setCausationId(correlationUuid);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(subjectId);
        outbox.setSubjectType(subjectType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    /** Outbox stores correlation as UUID; derive a stable UUID when the header is not RFC-4122. */
    private static UUID correlationUuidOrDerived(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(correlationId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
