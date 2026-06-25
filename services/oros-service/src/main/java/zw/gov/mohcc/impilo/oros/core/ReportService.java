package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Diagnostic reporting lifecycle (spec §10): preliminary → final → amendment/addendum → release
 * → acknowledge, with an <b>immutable version chain</b>.
 *
 * <p>A final report is never overwritten. Amendments and addenda create a <em>new</em> versioned
 * {@link ResultEntity} that supersedes the prior head (via {@code supersedesResultId}), preserving
 * the full history. Each step writes a {@code RESULT_*} outbox event (the audit trail) and, for
 * imaging orders, best-effort syncs the fine-grained {@link ImagingWorkflowState} when the
 * transition is legal.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ResultRepository resultRepository;
    private final OrderStateMachine stateMachine;
    private final ImagingWorkflowService imagingWorkflowService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReportService(ResultRepository resultRepository,
                         OrderStateMachine stateMachine,
                         ImagingWorkflowService imagingWorkflowService,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.stateMachine = stateMachine;
        this.imagingWorkflowService = imagingWorkflowService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Author a preliminary report (version 1 of the chain if none exists). */
    @Transactional
    public ResultEntity createPreliminary(String orderId, String summaryJson, String impression,
                                          String recommendations, String docIds, String ziboCodes) {
        OrderEntity order = stateMachine.getOrder(orderId);
        ResultEntity r = newResult(order, summaryJson, impression, recommendations, docIds, ziboCodes,
                ResultStatus.PRELIMINARY, nextVersion(orderId), null);
        r = resultRepository.save(r);
        publishEvent(r, "RESULT_PRELIMINARY");
        syncImaging(order, ImagingWorkflowState.PRELIMINARY_REPORT);
        log.info("Preliminary report authored: orderId={}, resultId={}", orderId, r.getResultId());
        return r;
    }

    /** Author a final report. */
    @Transactional
    public ResultEntity createFinal(String orderId, String summaryJson, String impression,
                                    String recommendations, String docIds, String ziboCodes) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);
        ResultEntity r = newResult(order, summaryJson, impression, recommendations, docIds, ziboCodes,
                ResultStatus.FINAL, nextVersion(orderId), null);
        r.setValidatedBy(ctx.actorId());
        r = resultRepository.save(r);
        publishEvent(r, "RESULT_FINAL");
        syncImaging(order, ImagingWorkflowState.FINAL_REPORT);
        log.info("Final report authored: orderId={}, resultId={}", orderId, r.getResultId());
        return r;
    }

    /**
     * Amend a finalized report. Creates a new {@code AMENDED} version superseding the current head;
     * the prior final remains intact in the history chain.
     *
     * @throws IllegalStateException if there is no finalized report to amend
     */
    @Transactional
    public ResultEntity amend(String orderId, String reason, String summaryJson,
                              String impression, String recommendations) {
        return supersede(orderId, ResultStatus.AMENDED, "RESULT_AMENDED", reason,
                summaryJson, impression, recommendations, ImagingWorkflowState.AMENDED);
    }

    /**
     * Add an addendum to a finalized report (new {@code ADDENDUM} version superseding the head).
     *
     * @throws IllegalStateException if there is no finalized report to add to
     */
    @Transactional
    public ResultEntity addendum(String orderId, String reason, String summaryJson,
                                 String impression, String recommendations) {
        return supersede(orderId, ResultStatus.ADDENDUM, "RESULT_ADDENDUM", reason,
                summaryJson, impression, recommendations, ImagingWorkflowState.AMENDED);
    }

    /** Release the current head report to requesters/workspace/patient. */
    @Transactional
    public ResultEntity release(UUID resultId, String note) {
        ResultEntity r = load(resultId);
        r.setReleasedAt(OffsetDateTime.now());
        r = resultRepository.save(r);
        publishEvent(r, "RESULT_RELEASED");
        syncImaging(stateMachine.getOrder(r.getOrderId()), ImagingWorkflowState.RELEASED);
        log.info("Report released: resultId={}, orderId={}, note={}", resultId, r.getOrderId(), note);
        return r;
    }

    /** Flag a result as critical with a reason; emits the critical event for escalation. */
    @Transactional
    public ResultEntity flagCritical(UUID resultId, String reason) {
        ResultEntity r = load(resultId);
        r.setCritical(true);
        r.setCriticalReason(reason);
        r = resultRepository.save(r);
        publishEvent(r, "RESULT_CRITICAL");
        log.info("Result flagged critical: resultId={}, orderId={}", resultId, r.getOrderId());
        return r;
    }

    /** Acknowledge a (normal or critical) result; closes the result loop. */
    @Transactional
    public ResultEntity acknowledge(UUID resultId, String note) {
        TrustContext ctx = TrustContextHolder.require();
        ResultEntity r = load(resultId);
        r.setAcknowledgedAt(OffsetDateTime.now());
        r.setAcknowledgedBy(ctx.actorId());
        r = resultRepository.save(r);
        publishEvent(r, r.isCritical() ? "RESULT_CRITICAL_ACKNOWLEDGED" : "RESULT_ACKNOWLEDGED");
        syncImaging(stateMachine.getOrder(r.getOrderId()), ImagingWorkflowState.ACKNOWLEDGED);
        log.info("Result acknowledged: resultId={}, orderId={}, critical={}, note={}",
                resultId, r.getOrderId(), r.isCritical(), note);
        return r;
    }

    // ── internals ────────────────────────────────────────────────────────

    private ResultEntity supersede(String orderId, ResultStatus status, String eventType, String reason,
                                   String summaryJson, String impression, String recommendations,
                                   ImagingWorkflowState imagingTarget) {
        OrderEntity order = stateMachine.getOrder(orderId);
        ResultEntity head = resultRepository.findFirstByOrderIdAndReportStatusOrderByVersionDesc(
                        orderId, ResultStatus.FINAL)
                .or(() -> resultRepository.findFirstByOrderIdOrderByVersionDesc(orderId))
                .orElseThrow(() -> new IllegalStateException(
                        "No finalized report to " + status.name().toLowerCase() + " for order " + orderId));

        ResultEntity r = newResult(order,
                summaryJson != null ? summaryJson : head.getSummary(),
                impression != null ? impression : head.getImpression(),
                recommendations != null ? recommendations : head.getRecommendations(),
                head.getDocIds(), head.getZiboResultCodes(),
                status, head.getVersion() + 1, head.getResultId());
        r.setCriticalReason(reason);
        r = resultRepository.save(r);
        publishEvent(r, eventType);
        syncImaging(order, imagingTarget);
        log.info("Report {}: orderId={}, newResultId={}, supersedes={}, version={}",
                status, orderId, r.getResultId(), head.getResultId(), r.getVersion());
        return r;
    }

    private ResultEntity newResult(OrderEntity order, String summaryJson, String impression,
                                   String recommendations, String docIds, String ziboCodes,
                                   ResultStatus status, int version, UUID supersedes) {
        TrustContext ctx = TrustContextHolder.require();
        ResultEntity r = new ResultEntity();
        r.setOrderId(order.getOrderId());
        r.setKind(order.getOrderType() == OrderType.IMAGING ? ResultKind.IMAGING : ResultKind.LAB);
        r.setSummary(summaryJson != null ? summaryJson : "{}");
        r.setImpression(impression);
        r.setRecommendations(recommendations);
        r.setDocIds(docIds);
        r.setZiboResultCodes(ziboCodes);
        r.setReportStatus(status);
        r.setVersion(version);
        r.setSupersedesResultId(supersedes);
        r.setReportedBy(ctx.actorId());
        return r;
    }

    private int nextVersion(String orderId) {
        return resultRepository.findFirstByOrderIdOrderByVersionDesc(orderId)
                .map(r -> r.getVersion() + 1)
                .orElse(1);
    }

    private ResultEntity load(UUID resultId) {
        return resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found: " + resultId));
    }

    /** Best-effort imaging-state sync: only transitions when the guard permits it. */
    private void syncImaging(OrderEntity order, ImagingWorkflowState target) {
        if (order.getOrderType() != OrderType.IMAGING) {
            return;
        }
        if (!ImagingWorkflow.canTransition(order.getImagingState(), target)) {
            log.debug("Skipping imaging sync for order {}: {} -> {} not permitted",
                    order.getOrderId(), order.getImagingState(), target);
            return;
        }
        imagingWorkflowService.transition(order.getOrderId(), target, "report lifecycle");
    }

    private void publishEvent(ResultEntity r, String eventType) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("RESULT");
            event.setAggregateId(r.getResultId() != null ? r.getResultId().toString() : r.getOrderId());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(r));
            event.setTenantId(ctx.tenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write report outbox event {}: {}", eventType, e.getMessage(), e);
        }
    }
}
