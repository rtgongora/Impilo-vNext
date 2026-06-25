package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.core.workflow.FulfilmentWorkflow;
import zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Category-agnostic owner of fine-grained fulfilment-state ({@code oros_orders.workflow_state})
 * transitions for laboratory, procedure/assessment, and any other order category that has a
 * {@link FulfilmentWorkflow} guard. Imaging keeps its dedicated {@link ImagingWorkflowService}
 * (which carries imaging-specific side-effects such as PACS study linkage), but writes the same
 * shared {@code workflow_state} column in lockstep — so reconciliation/patient-file surfaces read
 * one unified lifecycle column regardless of category.
 *
 * <p>Every transition is validated against the deny-by-default guard selected by
 * {@link WorkflowGuardRegistry}, persisted, and recorded as a {@code WORKFLOW_STATE_<TARGET>}
 * outbox event (the event journal is the audit trail; authorization is enforced at the
 * Envoy/TSHEPO edge before the request reaches this service). The coarse canonical
 * {@link zw.gov.mohcc.impilo.oros.domain.OrderStatus} machine is intentionally left untouched.</p>
 */
@Service
public class FulfilmentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentWorkflowService.class);

    private final OrderRepository orderRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowGuardRegistry guards;

    public FulfilmentWorkflowService(OrderRepository orderRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     WorkflowGuardRegistry guards) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.guards = guards;
    }

    /**
     * Set the category entry state on a freshly submitted order (idempotent). Mutates the passed
     * entity and writes the entry event but does <em>not</em> persist — the caller (submission
     * flow) saves the order once. No-op for categories without a fine-grained workflow.
     */
    public void initialize(OrderEntity order, String reason) {
        if (order.getWorkflowState() != null) {
            return;
        }
        guards.forType(order.getOrderType()).ifPresent(guard -> {
            String entry = guard.entryState();
            applyState(order, entry);
            writeEvent(order, null, entry, reason);
            log.info("Fulfilment workflow initialized: orderId={}, type={}, state={}",
                    order.getOrderId(), order.getOrderType(), entry);
        });
    }

    /**
     * Transition an order to {@code targetState}, validating against the category guard.
     *
     * @throws IllegalStateException if the category has no fine-grained workflow or the transition is illegal
     */
    @Transactional
    public OrderEntity transition(String orderId, String targetState, String reason) {
        OrderEntity order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        FulfilmentWorkflow guard = guards.require(order.getOrderType());
        String from = order.getWorkflowState();
        guard.require(from, targetState);

        applyState(order, targetState);
        order = orderRepository.save(order);
        writeEvent(order, from, targetState, reason);

        log.info("Fulfilment workflow transitioned: orderId={}, type={}, {} -> {}",
                order.getOrderId(), order.getOrderType(), from, targetState);
        return order;
    }

    /** Legal next states from the order's current workflow state. */
    @Transactional(readOnly = true)
    public java.util.Set<String> allowedNext(String orderId) {
        OrderEntity order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        return guards.forType(order.getOrderType())
                .map(g -> g.allowedFrom(order.getWorkflowState()))
                .orElseGet(java.util.Set::of);
    }

    /**
     * Write the unified {@code workflow_state}. For IMAGING orders the {@code imaging_state}
     * projection is kept in lockstep so the existing imaging worklist/reconciliation views work.
     */
    private void applyState(OrderEntity order, String state) {
        order.setWorkflowState(state);
        if (order.getOrderType() == OrderType.IMAGING) {
            try {
                order.setImagingState(ImagingWorkflowState.valueOf(state));
            } catch (IllegalArgumentException ignored) {
                // Non-imaging state name on an imaging order — leave the projection untouched.
            }
        }
    }

    private void writeEvent(OrderEntity order, String from, String to, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("orderType", order.getOrderType().name());
        payload.put("from", from);
        payload.put("to", to);
        payload.put("accessionNumber", order.getAccessionNumber());
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("actorId", ctx.actorId());
        payload.put("reason", reason);
        payload.put("at", OffsetDateTime.now().toString());

        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("ORDER");
            event.setAggregateId(order.getOrderId());
            event.setEventType("WORKFLOW_STATE_" + to);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(ctx.tenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write fulfilment workflow outbox event for order {}: {}",
                    order.getOrderId(), e.getMessage(), e);
        }
    }
}
