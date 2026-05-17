package zw.gov.mohcc.impilo.community.social.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.community.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.community.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists social-domain events to the existing community-service outbox so the
 * existing CommunityOutboxPublisher relays them to Kafka with the same trust
 * envelope used by CHW events.
 */
@Component
public class SocialEventEmitter {

    private static final String PRODUCER = "community-service";

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SocialEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void emit(UUID tenantId,
                     String eventType,
                     String aggregateType,
                     String aggregateId,
                     Map<String, Object> payload) {
        EventOutboxEntity entity = new EventOutboxEntity();
        entity.setTenantId(tenantId);
        entity.setEventType(eventType);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setSubjectType(aggregateType);
        entity.setSubjectId(aggregateId);
        entity.setPartitionKey(aggregateId);
        entity.setOccurredAt(OffsetDateTime.now());
        entity.setProducer(PRODUCER);
        entity.setIdempotencyKey(eventType + ":" + aggregateId + ":" + UUID.randomUUID());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("payload", payload);
        entity.setPayloadJson(SocialJson.writeJson(objectMapper, envelope));
        outboxRepository.save(entity);
    }
}
