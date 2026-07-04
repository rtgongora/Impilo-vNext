package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single owner of fine-grained imaging-workflow state ({@link ImagingWorkflowState}) transitions
 * for {@code IMAGING} orders.
 *
 * <p>Every transition is validated against the deny-by-default {@link ImagingWorkflow} guard,
 * persisted to {@code oros_orders.imaging_state}, and recorded as an
 * {@code IMAGING_STATE_<TARGET>} outbox event carrying the actor, prior state, and reason — the
 * event journal is the audit trail (authorization is enforced at the Envoy/TSHEPO ext_authz
 * edge before the request reaches this service).</p>
 *
 * <p>The coarse canonical {@link zw.gov.mohcc.impilo.oros.domain.OrderStatus} machine is
 * intentionally left untouched: imaging detail rides on {@code imagingState} so the
 * platform-wide lab/pharmacy lifecycle is unaffected.</p>
 */
@Service
public class ImagingWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ImagingWorkflowService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration;

    public ImagingWorkflowService(OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper,
                                  zw.gov.mohcc.impilo.oros.integration.ButanoIntegration butanoIntegration) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.butanoIntegration = butanoIntegration;
    }

    /**
     * Set the entry-point imaging state {@code RECEIVED} on a freshly submitted imaging order.
     *
     * <p>Mutates the passed entity and writes the {@code IMAGING_STATE_RECEIVED} event but does
     * <em>not</em> persist — the caller (submission flow) saves the order once. Idempotent: if
     * the order already has an imaging state, this is a no-op.</p>
     */
    public void initializeReceived(OrderEntity order, String reason) {
        requireImaging(order);
        if (order.getImagingState() != null) {
            return;
        }
        ImagingWorkflow.require(null, ImagingWorkflowState.RECEIVED);
        order.setImagingState(ImagingWorkflowState.RECEIVED);
        order.setWorkflowState(ImagingWorkflowState.RECEIVED.name());
        writeEvent(order, null, ImagingWorkflowState.RECEIVED, reason);
        log.info("Imaging workflow initialized: orderId={}, state=RECEIVED", order.getOrderId());
    }

    /**
     * Transition an imaging order to {@code target}, validating against the workflow guard.
     *
     * @throws IllegalStateException if the order is not an imaging order or the transition is illegal
     */
    @Transactional
    public OrderEntity transition(String orderId, ImagingWorkflowState target, String reason) {
        return doTransition(load(orderId), target, reason);
    }

    /**
     * Schedule an imaging order: set the acquisition/appointment time and drive the workflow to
     * {@code SCHEDULED} in one atomic step.
     *
     * @throws IllegalStateException if the order is not imaging or is not in a schedulable state
     */
    @Transactional
    public OrderEntity schedule(String orderId, OffsetDateTime scheduledAt, String note) {
        OrderEntity order = load(orderId);
        if (scheduledAt != null) {
            order.setScheduledAt(scheduledAt);
        }
        return doTransition(order, ImagingWorkflowState.SCHEDULED, note);
    }

    /**
     * Link a fulfilled exam's PACS/DICOM study to the order and drive the workflow to
     * {@code IMAGES_LINKED} in one atomic step (criterion F). Records the study UID and viewer
     * URL on the order; sets the accession number too if not already reserved.
     *
     * @throws IllegalStateException if the order is not imaging or is not ready for image linkage
     */
    @Transactional
    public OrderEntity linkStudy(String orderId, String studyUid, String viewerUrl, String accessionNumber) {
        OrderEntity order = load(orderId);
        if (studyUid != null && !studyUid.isBlank()) {
            order.setStudyUid(studyUid);
        }
        if (viewerUrl != null && !viewerUrl.isBlank()) {
            order.setStudyViewerUrl(viewerUrl);
        }
        if (accessionNumber != null && !accessionNumber.isBlank() && order.getAccessionNumber() == null) {
            order.setAccessionNumber(accessionNumber);
        }
        OrderEntity linked = doTransition(order, ImagingWorkflowState.IMAGES_LINKED, "study linked: " + studyUid);
        // Publish the linked study to the SHR as a FHIR ImagingStudy (flag-gated, best-effort).
        butanoIntegration.createImagingStudy(linked, firstItemModality(linked));
        return linked;
    }

    /**
     * Best-effort modality hint from the order's first imaging item carrying one (null if none).
     * Used for FHIR ImagingStudy modality and DICOM MWL scheduled-procedure-step modality; callers
     * must tolerate null rather than guess.
     */
    public String firstItemModality(OrderEntity order) {
        try {
            return orderItemRepository.findByOrderId(order.getOrderId()).stream()
                    .map(OrderItemEntity::getModality)
                    .filter(m -> m != null && !m.isBlank())
                    .findFirst()
                    .map(m -> m.trim().toUpperCase(java.util.Locale.ROOT))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve item modality for orderId={}: {}", order.getOrderId(), e.getMessage());
            return null;
        }
    }

    private OrderEntity doTransition(OrderEntity order, ImagingWorkflowState target, String reason) {
        requireImaging(order);
        ImagingWorkflowState from = order.getImagingState();
        ImagingWorkflow.require(from, target);

        order.setImagingState(target);
        order.setWorkflowState(target.name());
        order = orderRepository.save(order);
        writeEvent(order, from, target, reason);

        log.info("Imaging workflow transitioned: orderId={}, {} -> {}", order.getOrderId(), from, target);
        return order;
    }

    private OrderEntity load(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private void requireImaging(OrderEntity order) {
        if (order.getOrderType() != OrderType.IMAGING) {
            throw new IllegalStateException(
                    "Imaging workflow is only valid for IMAGING orders; order " + order.getOrderId()
                            + " is " + order.getOrderType());
        }
    }

    private void writeEvent(OrderEntity order, ImagingWorkflowState from,
                            ImagingWorkflowState to, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("orderType", order.getOrderType().name());
        payload.put("from", from != null ? from.name() : null);
        payload.put("to", to.name());
        payload.put("accessionNumber", order.getAccessionNumber());
        payload.put("studyUid", order.getStudyUid());
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("actorId", ctx.actorId());
        payload.put("reason", reason);
        payload.put("at", OffsetDateTime.now().toString());

        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("ORDER");
            event.setAggregateId(order.getOrderId());
            event.setEventType("IMAGING_STATE_" + to.name());
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(ctx.tenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write imaging workflow outbox event for order {}: {}",
                    order.getOrderId(), e.getMessage(), e);
        }
    }
}
