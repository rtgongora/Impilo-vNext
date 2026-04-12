package zw.gov.mohcc.impilo.air.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.air.persistence.entity.AiEventOutboxEntity;
import zw.gov.mohcc.impilo.air.persistence.repository.AiEventOutboxRepository;

@Service
public class AiRegistryOutboxAppender {

    private static final Logger log = LoggerFactory.getLogger(AiRegistryOutboxAppender.class);

    private final AiEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AiRegistryOutboxAppender(AiEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void append(
            UUID tenantId,
            String podId,
            UUID correlationId,
            UUID causationId,
            String idempotencyKey,
            String aggregateType,
            String aggregateId,
            String eventType,
            String subjectType,
            String subjectId,
            String partitionKey,
            Map<String, Object> payload) {
        try {
            AiEventOutboxEntity row = new AiEventOutboxEntity();
            row.setTenantId(tenantId);
            row.setPodId(podId != null ? podId : "national-spine");
            row.setCorrelationId(correlationId);
            row.setCausationId(causationId);
            row.setIdempotencyKey(idempotencyKey);
            row.setAggregateType(aggregateType);
            row.setAggregateId(aggregateId);
            row.setEventType(eventType);
            row.setSubjectType(subjectType);
            row.setSubjectId(subjectId);
            row.setPartitionKey(partitionKey != null ? partitionKey : aggregateId);
            row.setPayloadJson(objectMapper.writeValueAsString(payload != null ? payload : new LinkedHashMap<>()));
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            log.warn("AI registry outbox serialization failed: {}", e.getMessage());
        }
    }
}
