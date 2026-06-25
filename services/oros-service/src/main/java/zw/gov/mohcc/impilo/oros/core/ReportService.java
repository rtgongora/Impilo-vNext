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
import java.util.LinkedHashMap;
import java.util.Map;
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

    /** Logical step in the report lifecycle, mapped to a category-specific workflow state. */
    public enum ReportStep { PRELIMINARY, FINAL, RELEASED, ACKNOWLEDGED, AMENDED }

    private final ResultRepository resultRepository;
    private final OrderStateMachine stateMachine;
    private final ImagingWorkflowService imagingWorkflowService;
    private final zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration;
    private final zw.gov.mohcc.impilo.oros.integration.hl7.Hl7OruSender hl7OruSender;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry guards;
    private final FulfilmentWorkflowService fulfilmentWorkflowService;

    public ReportService(ResultRepository resultRepository,
                         OrderStateMachine stateMachine,
                         ImagingWorkflowService imagingWorkflowService,
                         zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration,
                         zw.gov.mohcc.impilo.oros.integration.hl7.Hl7OruSender hl7OruSender,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper,
                         zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry guards,
                         FulfilmentWorkflowService fulfilmentWorkflowService) {
        this.resultRepository = resultRepository;
        this.stateMachine = stateMachine;
        this.imagingWorkflowService = imagingWorkflowService;
        this.butanoIntegration = butanoIntegration;
        this.hl7OruSender = hl7OruSender;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.guards = guards;
        this.fulfilmentWorkflowService = fulfilmentWorkflowService;
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
        syncWorkflow(order, ReportStep.PRELIMINARY);
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
        syncWorkflow(order, ReportStep.FINAL);
        butanoIntegration.createDiagnosticReport(orderId, r);
        hl7OruSender.sendFinalReport(order, r);
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
                summaryJson, impression, recommendations);
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
                summaryJson, impression, recommendations);
    }

    /** Release the current head report to requesters/workspace/patient. */
    @Transactional
    public ResultEntity release(UUID resultId, String note) {
        ResultEntity r = load(resultId);
        r.setReleasedAt(OffsetDateTime.now());
        r = resultRepository.save(r);
        publishEvent(r, "RESULT_RELEASED");
        syncWorkflow(stateMachine.getOrder(r.getOrderId()), ReportStep.RELEASED);
        log.info("Report released: resultId={}, orderId={}, note={}", resultId, r.getOrderId(), note);
        return r;
    }

    /**
     * Flag a result as critical with a reason; emits an enriched {@code RESULT_CRITICAL} event
     * (carrying the order's tenant, requester, patient, and accession) so downstream consumers
     * (notifications/escalation) can alert the requester without a second lookup.
     */
    @Transactional
    public ResultEntity flagCritical(UUID resultId, String reason) {
        ResultEntity r = load(resultId);
        r.setCritical(true);
        r.setCriticalReason(reason);
        r = resultRepository.save(r);

        OrderEntity order = stateMachine.getOrder(r.getOrderId());
        publishCriticalEvent(order, r, "RESULT_CRITICAL");
        log.info("Result flagged critical: resultId={}, orderId={}", resultId, r.getOrderId());
        return r;
    }

    /** Full version chain for an order (newest first) — the reporting history view. */
    public java.util.List<ResultEntity> versions(String orderId) {
        return resultRepository.findByOrderIdOrderByVersionDescCreatedAtDesc(orderId);
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
        syncWorkflow(stateMachine.getOrder(r.getOrderId()), ReportStep.ACKNOWLEDGED);
        log.info("Result acknowledged: resultId={}, orderId={}, critical={}, note={}",
                resultId, r.getOrderId(), r.isCritical(), note);
        return r;
    }

    // ── internals ────────────────────────────────────────────────────────

    private ResultEntity supersede(String orderId, ResultStatus status, String eventType, String reason,
                                   String summaryJson, String impression, String recommendations) {
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
        syncWorkflow(order, ReportStep.AMENDED);
        // SHR writeback with relatesTo lineage to the superseded report (§10).
        butanoIntegration.createDiagnosticReport(orderId, r);
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

    /**
     * Best-effort fine-grained workflow sync for a report-lifecycle step. Maps the logical step to
     * the category's workflow state and only transitions when the category guard permits it.
     * Imaging keeps its dedicated service (carrying PACS side-effects); lab/procedure go through
     * the generalised {@link FulfilmentWorkflowService}.
     */
    private void syncWorkflow(OrderEntity order, ReportStep step) {
        if (order.getOrderType() == OrderType.IMAGING) {
            ImagingWorkflowState target = imagingTarget(step);
            if (target != null && ImagingWorkflow.canTransition(order.getImagingState(), target)) {
                imagingWorkflowService.transition(order.getOrderId(), target, "report lifecycle");
            }
            return;
        }
        var guard = guards.forType(order.getOrderType());
        if (guard == null || guard.isEmpty()) {
            return;
        }
        String target = genericTarget(order.getOrderType(), step);
        if (target == null || !guard.get().canTransition(order.getWorkflowState(), target)) {
            log.debug("Skipping workflow sync for order {} ({}): {} -> {} not permitted",
                    order.getOrderId(), order.getOrderType(), order.getWorkflowState(), target);
            return;
        }
        fulfilmentWorkflowService.transition(order.getOrderId(), target, "report lifecycle");
    }

    private static ImagingWorkflowState imagingTarget(ReportStep step) {
        return switch (step) {
            case PRELIMINARY -> ImagingWorkflowState.PRELIMINARY_REPORT;
            case FINAL -> ImagingWorkflowState.FINAL_REPORT;
            case RELEASED -> ImagingWorkflowState.RELEASED;
            case ACKNOWLEDGED -> ImagingWorkflowState.ACKNOWLEDGED;
            case AMENDED -> ImagingWorkflowState.AMENDED;
        };
    }

    /** State-name mapping for the report lifecycle of non-imaging categories. */
    private static String genericTarget(OrderType type, ReportStep step) {
        boolean lab = type == OrderType.LAB;
        return switch (step) {
            case PRELIMINARY -> lab ? "PRELIMINARY_RESULT" : "PRELIMINARY_REPORT";
            case FINAL -> lab ? "FINAL_RESULT" : "FINAL_REPORT";
            case RELEASED -> "RELEASED";
            case ACKNOWLEDGED -> "ACKNOWLEDGED";
            case AMENDED -> "AMENDED";
        };
    }

    /**
     * Build the enriched critical-result event payload — order context (tenant/requester/patient/
     * accession) plus the critical reason — shared by initial flagging and escalation so consumers
     * see a consistent shape.
     */
    public static Map<String, Object> criticalPayload(OrderEntity order, ResultEntity r) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("resultId", r.getResultId() != null ? r.getResultId().toString() : null);
        p.put("orderId", order.getOrderId());
        p.put("tenantId", order.getTenantId() != null ? order.getTenantId().toString() : null);
        p.put("patientCpid", order.getPatientCpid());
        p.put("requesterId", order.getReferringProviderId() != null
                ? order.getReferringProviderId() : order.getPlacedBy());
        p.put("placedBy", order.getPlacedBy());
        p.put("referringProviderId", order.getReferringProviderId());
        p.put("accessionNumber", order.getAccessionNumber());
        p.put("criticalReason", r.getCriticalReason());
        p.put("reportStatus", r.getReportStatus() != null ? r.getReportStatus().name() : null);
        p.put("reportedBy", r.getReportedBy());
        return p;
    }

    private void publishCriticalEvent(OrderEntity order, ResultEntity r, String eventType) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("RESULT");
            event.setAggregateId(r.getResultId() != null ? r.getResultId().toString() : r.getOrderId());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(criticalPayload(order, r)));
            event.setTenantId(order.getTenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write critical outbox event {}: {}", eventType, e.getMessage(), e);
        }
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
