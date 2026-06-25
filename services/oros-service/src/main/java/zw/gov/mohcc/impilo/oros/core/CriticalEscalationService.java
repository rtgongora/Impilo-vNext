package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Escalates critical results that remain unacknowledged past the configured ack threshold
 * ({@code oros.escalation.ack-timeout-minutes}). Emits a single {@code ACK_ESCALATION} event per
 * result (routed to {@code oros.ack.escalation}) and stamps {@code escalated_at} so it fires once.
 *
 * <p>Runs as a tenant-agnostic scheduled sweep (no trust context); tenant identity is taken from
 * each result's order. The unacknowledged-critical list itself is also queryable on demand via the
 * reconciliation dashboard.</p>
 */
@Service
public class CriticalEscalationService {

    private static final Logger log = LoggerFactory.getLogger(CriticalEscalationService.class);

    private final ResultRepository resultRepository;
    private final OrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OrosProperties properties;

    public CriticalEscalationService(ResultRepository resultRepository,
                                     OrderRepository orderRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     OrosProperties properties) {
        this.resultRepository = resultRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Scheduled sweep using the configured ack-timeout threshold. */
    @Scheduled(fixedDelayString = "${oros.escalation.sweep-interval-ms:60000}")
    public void sweep() {
        escalateOverdue(properties.getEscalation().getAckTimeoutMinutes());
    }

    /**
     * Escalate critical results older than {@code thresholdMinutes} that are still unacknowledged
     * and not yet escalated.
     *
     * @return the number of results escalated
     */
    @Transactional
    public int escalateOverdue(int thresholdMinutes) {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(thresholdMinutes);
        List<ResultEntity> overdue = resultRepository
                .findByIsCriticalTrueAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndCreatedAtBefore(threshold);

        int escalated = 0;
        for (ResultEntity r : overdue) {
            OrderEntity order = orderRepository.findByOrderId(r.getOrderId()).orElse(null);
            if (order == null) {
                continue;
            }
            r.setEscalatedAt(OffsetDateTime.now());
            resultRepository.save(r);
            emitEscalation(order, r);
            escalated++;
        }
        if (escalated > 0) {
            log.info("Escalated {} unacknowledged critical result(s) past {} minutes",
                    escalated, thresholdMinutes);
        }
        return escalated;
    }

    private void emitEscalation(OrderEntity order, ResultEntity r) {
        try {
            Map<String, Object> payload = ReportService.criticalPayload(order, r);
            payload.put("escalation", true);
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("RESULT");
            event.setAggregateId(r.getResultId() != null ? r.getResultId().toString() : r.getOrderId());
            event.setEventType("ACK_ESCALATION");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(order.getTenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write ACK_ESCALATION event for result {}: {}",
                    r.getResultId(), e.getMessage(), e);
        }
    }
}
