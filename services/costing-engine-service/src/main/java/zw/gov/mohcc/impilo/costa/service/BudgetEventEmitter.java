package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;

import java.util.Map;
import java.util.UUID;

/** Writes budget domain events to the shared COSTA outbox (published by OutboxPublisher). */
@Component
public class BudgetEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(BudgetEventEmitter.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BudgetEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void emit(String aggregateType, String aggregateId, String eventType,
                     UUID tenantId, Map<String, Object> payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write budget outbox event: {}", eventType, e);
        }
    }
}
