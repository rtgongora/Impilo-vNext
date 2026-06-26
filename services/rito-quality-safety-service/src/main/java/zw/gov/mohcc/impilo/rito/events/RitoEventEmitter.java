package zw.gov.mohcc.impilo.rito.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Writes domain events into the Rito transactional outbox. Called inside the same
 * transaction as the state change so events are never lost (relayed by
 * {@link RitoOutboxPublisher}).
 */
@Component
public class RitoEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(RitoEventEmitter.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RitoEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
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
            if (ctx != null && ctx.correlationId() != null) {
                event.setCorrelationId(ctx.correlationId());
            }
        } catch (IllegalStateException ignored) {
            // no trust context in tests / async paths
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
