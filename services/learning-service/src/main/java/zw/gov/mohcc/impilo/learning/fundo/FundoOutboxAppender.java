package zw.gov.mohcc.impilo.learning.fundo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;

/**
 * Small focused component used by the native Fundo services to append event
 * rows to {@code lrn_event_outbox}.
 *
 * <p>Deliberately separate from {@code LearningPlatformFacade.appendOutbox(...)}
 * — the legacy facade's private helper is reserved for the seven legacy
 * event-type emissions (and their Phase 4B dual-emit v1.1 counterparts) that
 * pre-date Phase 5. New native Fundo emissions write a single v1.1 row
 * because there is no legacy event-type counterpart to preserve.</p>
 *
 * <p>Uses the same {@code lrn_event_outbox} table and is published by the
 * same {@code LearningOutboxPublisher} introduced in Phase 2 — no new
 * topic, no new publisher, no new infrastructure.</p>
 */
@Component
public class FundoOutboxAppender {

    private final LearningOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FundoOutboxAppender(LearningOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void append(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        LearningOutboxEntity e = new LearningOutboxEntity();
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        try {
            e.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            e.setPayloadJson("{}");
        }
        outboxRepository.save(e);
    }
}
