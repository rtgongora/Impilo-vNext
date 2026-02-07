package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Manages result capture and retrieval for clinical orders.
 */
@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final ResultRepository resultRepository;
    private final OrderStateMachine stateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ResultService(ResultRepository resultRepository,
                         OrderStateMachine stateMachine,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.stateMachine = stateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Post a result for an order. Transitions the order to RESULT_AVAILABLE
     * or PARTIAL_RESULT depending on completeness.
     */
    @Transactional
    public ResultEntity postResult(String orderId, ResultKind kind, String summary,
                                   String ziboResultCodes, String docIds, boolean isCritical) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);

        ResultEntity result = new ResultEntity();
        result.setOrderId(orderId);
        result.setKind(kind);
        result.setSummary(summary);
        result.setZiboResultCodes(ziboResultCodes);
        result.setDocIds(docIds);
        result.setCritical(isCritical);
        result.setReportedBy(ctx.actorId());
        result = resultRepository.save(result);

        // Transition order to RESULT_AVAILABLE if currently IN_PROGRESS or PARTIAL_RESULT
        OrderStatus current = order.getStatus();
        if (current == OrderStatus.IN_PROGRESS || current == OrderStatus.PARTIAL_RESULT) {
            stateMachine.transition(order, OrderStatus.RESULT_AVAILABLE);
        }

        publishEvent("RESULT", result.getResultId().toString(),
                isCritical ? "CRITICAL_RESULT_POSTED" : "RESULT_POSTED",
                result, ctx.tenantId());

        log.info("Result posted: orderId={}, resultId={}, kind={}, critical={}",
                orderId, result.getResultId(), kind, isCritical);
        return result;
    }

    /**
     * Mark an existing result as critical.
     */
    @Transactional
    public ResultEntity markCritical(UUID resultId) {
        TrustContext ctx = TrustContextHolder.require();

        ResultEntity result = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found: " + resultId));

        result.setCritical(true);
        result = resultRepository.save(result);

        publishEvent("RESULT", resultId.toString(), "RESULT_MARKED_CRITICAL",
                result, ctx.tenantId());

        log.info("Result marked critical: resultId={}, orderId={}", resultId, result.getOrderId());
        return result;
    }

    /**
     * Get all results for an order, ordered by creation time descending.
     */
    public List<ResultEntity> getResults(String orderId) {
        return resultRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    /**
     * Acknowledge a result (delegates to AcknowledgementService).
     */
    public ResultEntity acknowledgeResult(UUID resultId) {
        return resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found: " + resultId));
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
