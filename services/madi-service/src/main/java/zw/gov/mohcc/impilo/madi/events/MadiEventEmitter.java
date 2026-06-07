package zw.gov.mohcc.impilo.madi.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.madi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class MadiEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(MadiEventEmitter.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public MadiEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void emit(String aggregateType, String aggregateId, String eventType,
                     String subjectType, String subjectId, Map<String, Object> payload, UUID tenantId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setTenantId(tenantId);
        event.setPartitionKey(aggregateId);
        event.setIdempotencyKey(aggregateType + ":" + aggregateId + ":" + eventType + ":" + UUID.randomUUID());
        event.setOccurredAt(OffsetDateTime.now());
        try {
            TrustContext ctx = TrustContextHolder.get();
            if (ctx != null) {
                if (ctx.correlationId() != null) {
                    event.setCorrelationId(ctx.correlationId());
                }
            }
        } catch (IllegalStateException ignored) {
            // no trust context in tests
        }
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for {}: {}", eventType, e.getMessage());
            event.setPayloadJson("{}");
        }
        outboxRepository.save(event);
    }
}
