package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.simba.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Appends Simba wellness-depth domain events to the outbox (plan enrolment, task completion,
 * coaching, care linkage, reminder requests). Mirrors the outbox-write idiom used by
 * {@code ConnectIngestService}; the {@code SimbaOutboxPublisher} drains it to Kafka.
 */
@Service
public class SimbaEventEmitter {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SimbaEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void emit(UUID tenantId,
                     String aggregateType,
                     String aggregateId,
                     String eventType,
                     String subjectCpid,
                     String idempotencyKey,
                     Map<String, Object> payload) {
        try {
            EventOutboxEntity out = new EventOutboxEntity();
            out.setAggregateType(aggregateType);
            out.setAggregateId(aggregateId);
            out.setEventType(eventType);
            out.setSchemaVersion(1);
            out.setIdempotencyKey(idempotencyKey);
            out.setTenantId(tenantId);
            out.setSubjectType("PERSON");
            out.setSubjectId(subjectCpid);
            out.setPartitionKey(subjectCpid);
            out.setOccurredAt(OffsetDateTime.now());
            out.setPayloadJson(objectMapper.writeValueAsString(payload));

            var ctx = RequestContextHolder.get();
            if (ctx != null) {
                out.setPodId(ctx.podId());
                try {
                    out.setCorrelationId(UUID.fromString(ctx.correlationId()));
                } catch (Exception ignored) {
                    // non-UUID correlation id — leave null
                }
            } else {
                out.setPodId("national-spine");
            }
            outboxRepository.save(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Simba event payload", e);
        }
    }
}
