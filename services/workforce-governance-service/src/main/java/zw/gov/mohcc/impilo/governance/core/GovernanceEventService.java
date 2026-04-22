package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.governance.persistence.EventOutboxRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class GovernanceEventService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceEventService.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GovernanceEventService(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload,
                        UUID tenantId, String correlationId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            EventOutboxEntity row = EventOutboxEntity.pending(
                    aggregateType,
                    aggregateId,
                    eventType,
                    json,
                    tenantId != null ? tenantId.toString() : null,
                    correlationId
            );
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize governance event {} — skipping outbox: {}", eventType, e.getMessage());
        }
    }
}
