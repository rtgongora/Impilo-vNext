package zw.gov.mohcc.impilo.guidance.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.NompiloHandoffEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes guidance domain events to the transactional outbox (same pattern as TSHEPO consent).
 */
@Service
public class GuidanceOutboxAppender {

    private static final Logger log = LoggerFactory.getLogger(GuidanceOutboxAppender.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GuidanceOutboxAppender(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a Nompilo handoff lifecycle event (requested / accepted / escalated / closed).
     * The create event uses the canonical {@code core.nompilo.handoff.requested} name so existing
     * subscribers (operations desk, Rito feedback routing) can react.
     */
    @Transactional
    public void recordHandoffEvent(NompiloHandoffEntity h, String eventType) {
        try {
            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType("NOMPILO_HANDOFF");
            row.setAggregateId(h.getId().toString());
            row.setEventType(eventType);
            // Each lifecycle transition is a distinct event; random suffix avoids key collisions
            // (e.g. repeated escalation) while staying readable — same approach as the ask event.
            row.setIdempotencyKey(eventType + "-" + h.getId() + "-" + UUID.randomUUID());
            row.setTenantId(h.getTenantId());
            row.setSubjectType("ACTOR");
            String subject = h.getSubjectId() != null ? h.getSubjectId()
                    : (h.getActorId() != null ? h.getActorId() : "unknown");
            row.setSubjectId(subject);
            row.setPartitionKey(h.getTenantId() + ":" + h.getId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("handoffId", h.getId().toString());
            payload.put("status", h.getStatus());
            payload.put("priority", h.getPriority());
            payload.put("destination", h.getDestination());
            payload.put("transactionId", h.getTransactionId());
            payload.put("subjectId", h.getSubjectId());
            payload.put("flowId", h.getFlowId());
            payload.put("assignedTo", h.getAssignedTo());
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            log.warn("Guidance handoff outbox serialization failed: {}", e.getMessage());
        }
    }

    /**
     * Records a guidance ask interaction (POST /ask) for downstream subscribers.
     */
    @Transactional
    public void recordAskCompleted(String tenantId, String actorId, String question,
                                   boolean personalized, Map<String, Object> askResult) {
        try {
            EventOutboxEntity row = new EventOutboxEntity();
            String aggregateId = UUID.randomUUID().toString();
            row.setAggregateType("GUIDANCE");
            row.setAggregateId(aggregateId);
            row.setEventType("GUIDANCE_ASK_COMPLETED");
            row.setIdempotencyKey("guidance-ask-" + aggregateId);
            row.setTenantId(tenantId);
            row.setSubjectType("ACTOR");
            row.setSubjectId(actorId);
            row.setPartitionKey(tenantId + ":" + actorId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("personalized", personalized);
            payload.put("questionLength", question != null ? question.length() : 0);
            if (askResult != null && askResult.get("confidence") != null) {
                payload.put("confidence", askResult.get("confidence"));
            }
            row.setPayloadJson(objectMapper.writeValueAsString(payload));
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            log.warn("Guidance outbox payload serialization failed: {}", e.getMessage());
        }
    }
}
