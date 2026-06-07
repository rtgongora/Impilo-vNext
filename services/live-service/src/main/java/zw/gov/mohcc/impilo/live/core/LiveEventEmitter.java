package zw.gov.mohcc.impilo.live.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.live.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LiveEventEmitter {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LiveEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void emit(UUID tenantId,
                     String aggregateType,
                     String aggregateId,
                     String eventType,
                     String subjectType,
                     String subjectId,
                     Map<String, Object> payload) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setTenantId(tenantId);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setSubjectType(subjectType);
        entity.setSubjectId(subjectId);
        entity.setPartitionKey(aggregateId);
        entity.setOccurredAt(OffsetDateTime.now());
        entity.setIdempotencyKey(eventType + ":" + aggregateId + ":" + UUID.randomUUID());

        try {
            var ctx = RequestContextHolder.get();
            if (ctx != null) {
                entity.setPodId(ctx.podId());
                if (ctx.correlationId() != null && !ctx.correlationId().isBlank()) {
                    entity.setCorrelationId(UUID.fromString(ctx.correlationId()));
                }
            } else {
                entity.setPodId("national-spine");
            }
        } catch (Exception ignored) {
            entity.setPodId("national-spine");
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("payload", payload);
        try {
            entity.setPayloadJson(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            entity.setPayloadJson("{}");
        }
        outboxRepository.save(entity);
    }
}
