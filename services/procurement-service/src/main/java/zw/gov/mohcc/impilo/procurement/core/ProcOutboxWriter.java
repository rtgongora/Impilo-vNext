package zw.gov.mohcc.impilo.procurement.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procurement.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.procurement.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.util.Map;
import java.util.UUID;

@Service
public class ProcOutboxWriter {
    private final EventOutboxRepository repo;
    private final ObjectMapper mapper;
    private final EventTopicRegistry reg = new EventTopicRegistry("procurement");

    public ProcOutboxWriter(EventOutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public void publish(UUID tenantId, String aggregateType, String aggregateId, String entity, String action,
                        String idempotencyKey, Map<String, Object> payload) throws Exception {
        EventOutboxEntity e = new EventOutboxEntity();
        e.setTenantId(tenantId);
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(reg.eventType(entity, action));
        e.setIdempotencyKey(idempotencyKey);
        e.setSubjectType(aggregateType);
        e.setSubjectId(aggregateId);
        e.setPartitionKey(aggregateId);
        e.setPayloadJson(mapper.writeValueAsString(payload));
        repo.save(e);
    }
}
