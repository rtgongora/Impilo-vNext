package zw.gov.mohcc.impilo.channels.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.channels.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.channels.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void persistEvent(RequestContext ctx, String eventType, String aggregateType,
                             String aggregateId, String subjectType, String subjectId,
                             String idempotencyKey, Map<String, Object> payload) {
        OffsetDateTime now = OffsetDateTime.now();

        EventEnvelope envelope = EventEnvelope.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .correlationId(ctx.correlationId())
                .causationId(ctx.requestId())
                .idempotencyKey(idempotencyKey)
                .producer("channels-service")
                .tenantId(ctx.tenantId())
                .podId(ctx.podId())
                .occurredAt(now)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .payload(payload)
                .meta(Map.of("partition_key", ctx.tenantId()))
                .build();

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setEventId(java.util.UUID.fromString(envelope.eventId()));
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setCausationId(ctx.requestId());
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setSubjectType(subjectType);
        outbox.setSubjectId(subjectId);
        outbox.setOccurredAt(now);
        outbox.setPayloadJson(toJson(envelope));

        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
