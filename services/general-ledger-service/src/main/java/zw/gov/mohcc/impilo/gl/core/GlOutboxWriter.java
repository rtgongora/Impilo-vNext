package zw.gov.mohcc.impilo.gl.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.util.Map;
import java.util.UUID;

@Service
public class GlOutboxWriter {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final EventTopicRegistry registry = new EventTopicRegistry("general_ledger");

    public GlOutboxWriter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publish(UUID tenantId, String aggregateType, String aggregateId, String entity, String action,
                        String idempotencyKey, Map<String, Object> payload) {
        EventOutboxEntity row = new EventOutboxEntity();
        row.setTenantId(tenantId);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setEventType(registry.eventType(entity, action));
        row.setIdempotencyKey(idempotencyKey);
        row.setSubjectType(aggregateType);
        row.setSubjectId(aggregateId);
        row.setPartitionKey(aggregateId);
        try {
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        outboxRepository.save(row);
    }
}
