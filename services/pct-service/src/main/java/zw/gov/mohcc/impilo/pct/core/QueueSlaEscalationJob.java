package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.QueueItemRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SLA breach sweep: WAITING queue items past their {@code sla_due_at} that
 * have not been escalated yet are escalated (priority raised to at least 5,
 * escalation audit fields stamped) and a
 * {@code pct.telemedicine.queue.sla.breached} event is emitted for the comms
 * plane. System-initiated: runs without a TrustContext, so escalation is
 * applied directly with an explicit system actor rather than through the
 * TrustContext-bound {@link QueueEngine#escalateItem}.
 */
@Component
public class QueueSlaEscalationJob {

    public static final String SYSTEM_ACTOR = "system:sla-monitor";

    private static final Logger log = LoggerFactory.getLogger(QueueSlaEscalationJob.class);

    private final QueueItemRepository queueItemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public QueueSlaEscalationJob(QueueItemRepository queueItemRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.queueItemRepository = queueItemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${pct.telemedicine.sla.sweep-interval-ms:60000}",
            initialDelayString = "${pct.telemedicine.sla.sweep-initial-delay-ms:30000}")
    public void sweep() {
        int escalated = escalateBreachedItems();
        if (escalated > 0) {
            log.info("SLA sweep escalated {} queue item(s)", escalated);
        }
    }

    /** Escalate every WAITING item past its SLA deadline (not yet escalated). Returns the count. */
    @Transactional
    public int escalateBreachedItems() {
        OffsetDateTime now = OffsetDateTime.now();
        List<QueueItemEntity> breached = queueItemRepository
                .findTop100ByStatusAndSlaDueAtBeforeAndEscalatedAtIsNullOrderBySlaDueAtAsc(
                        QueueItemStatus.WAITING, now);
        for (QueueItemEntity item : breached) {
            int newPriority = Math.max(item.getPriority() + 2, 5);
            item.setPriority(newPriority);
            item.setEscalatedAt(now);
            item.setEscalatedBy(SYSTEM_ACTOR);
            item.setEscalationReason("SLA breached: due " + item.getSlaDueAt());
            queueItemRepository.save(item);

            // No null values in event payload maps (EventEnvelope Map.copyOf NPEs).
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", "QUEUE_SLA_BREACHED");
            payload.put("tenantId", item.getTenantId().toString());
            payload.put("queueId", item.getQueueId().toString());
            payload.put("itemId", item.getId().toString());
            if (item.getSourceRef() != null) payload.put("sourceRef", item.getSourceRef());
            if (item.getJourneyId() != null) payload.put("journeyId", item.getJourneyId());
            payload.put("patientCpid", item.getPatientCpid());
            payload.put("slaDueAt", item.getSlaDueAt().toString());
            payload.put("priority", newPriority);
            payload.put("escalatedBy", SYSTEM_ACTOR);

            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("QUEUE_ITEM");
            outbox.setAggregateId(item.getId().toString());
            outbox.setEventType("QUEUE_SLA_BREACHED");
            outbox.setTenantId(item.getTenantId());
            try {
                outbox.setPayload(objectMapper.writeValueAsString(payload));
            } catch (Exception e) {
                outbox.setPayload("{}");
            }
            outboxRepository.save(outbox);

            log.warn("SLA breach: queue item {} (queue {}, sourceRef {}) past {} — escalated to priority {}",
                    item.getId(), item.getQueueId(), item.getSourceRef(), item.getSlaDueAt(), newPriority);
        }
        return breached.size();
    }
}
