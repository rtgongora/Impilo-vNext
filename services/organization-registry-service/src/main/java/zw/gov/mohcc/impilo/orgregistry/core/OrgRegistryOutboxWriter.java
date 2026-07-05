package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.util.Map;
import java.util.UUID;

@Service
public class OrgRegistryOutboxWriter {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final EventTopicRegistry registry = new EventTopicRegistry("org-registry");

    public OrgRegistryOutboxWriter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publish(UUID tenantId, String aggregateType, String aggregateId, String entity, String action,
                        String idempotencyKey, Map<String, Object> payload) throws Exception {
        EventOutboxEntity e = new EventOutboxEntity();
        e.setTenantId(tenantId);
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(registry.eventType(entity, action));
        e.setIdempotencyKey(idempotencyKey);
        e.setSubjectType(aggregateType);
        e.setSubjectId(aggregateId);
        e.setPartitionKey(aggregateId);
        e.setPayloadJson(objectMapper.writeValueAsString(payload));
        outboxRepository.save(e);
    }
}
