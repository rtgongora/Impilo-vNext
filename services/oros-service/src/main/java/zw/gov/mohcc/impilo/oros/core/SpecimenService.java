package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.core.workflow.WorkflowGuardRegistry;
import zw.gov.mohcc.impilo.oros.domain.SpecimenStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.SpecimenRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the laboratory specimen lifecycle (spec §7): collection → labelling → dispatch → receipt,
 * plus the rejection / loss / recollection exceptions. Each specimen transition advances the
 * order's generalised LAB {@code workflow_state} (best-effort, via {@link FulfilmentWorkflowService}
 * when the {@code LabWorkflow} guard permits), appends a chain-of-custody entry, and emits a
 * {@code SPECIMEN_*} outbox event (the audit trail).
 */
@Service
public class SpecimenService {

    private static final Logger log = LoggerFactory.getLogger(SpecimenService.class);

    private final SpecimenRepository specimenRepository;
    private final OrderRepository orderRepository;
    private final FulfilmentWorkflowService fulfilmentWorkflowService;
    private final WorkflowGuardRegistry guards;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SpecimenService(SpecimenRepository specimenRepository,
                           OrderRepository orderRepository,
                           FulfilmentWorkflowService fulfilmentWorkflowService,
                           WorkflowGuardRegistry guards,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.specimenRepository = specimenRepository;
        this.orderRepository = orderRepository;
        this.fulfilmentWorkflowService = fulfilmentWorkflowService;
        this.guards = guards;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Record a specimen collected from the patient and advance the order to {@code COLLECTED}. */
    @Transactional
    public SpecimenEntity collect(String orderId, String specimenType, String specimenSource,
                                  String bodySite, String fastingStatus, String collectionSite,
                                  String collectionInstructions, String sampleId) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = loadOrder(orderId);

        SpecimenEntity s = new SpecimenEntity();
        s.setOrderId(orderId);
        s.setTenantId(order.getTenantId());
        s.setFacilityId(order.getFacilityId());
        s.setSpecimenType(specimenType);
        s.setSpecimenSource(specimenSource);
        s.setBodySite(bodySite);
        s.setFastingStatus(fastingStatus);
        s.setCollectionSite(collectionSite);
        s.setCollectionInstructions(collectionInstructions);
        s.setSampleId(sampleId != null && !sampleId.isBlank() ? sampleId : generateSampleId());
        s.setStatus(SpecimenStatus.COLLECTED);
        s.setCollectedBy(ctx.actorId());
        s.setCollectedAt(OffsetDateTime.now());
        appendCustody(s, SpecimenStatus.COLLECTED, ctx.actorId(), "collected");
        s = specimenRepository.save(s);

        advanceOrder(order, "COLLECTED", "specimen collected: " + s.getSampleId());
        emit(s, "SPECIMEN_COLLECTED");
        log.info("Specimen collected: orderId={}, specimenId={}, sampleId={}",
                orderId, s.getSpecimenId(), s.getSampleId());
        return s;
    }

    /** Apply/replace the label (barcode) on a collected specimen. */
    @Transactional
    public SpecimenEntity label(UUID specimenId, String sampleId) {
        TrustContext ctx = TrustContextHolder.require();
        SpecimenEntity s = load(specimenId);
        if (sampleId != null && !sampleId.isBlank()) {
            s.setSampleId(sampleId);
        }
        s.setStatus(SpecimenStatus.LABELLED);
        appendCustody(s, SpecimenStatus.LABELLED, ctx.actorId(), "labelled");
        s = specimenRepository.save(s);
        emit(s, "SPECIMEN_LABELLED");
        return s;
    }

    /** Dispatch a specimen toward the analysing lab; advances the order to {@code DISPATCHED}. */
    @Transactional
    public SpecimenEntity dispatch(UUID specimenId) {
        TrustContext ctx = TrustContextHolder.require();
        SpecimenEntity s = load(specimenId);
        s.setStatus(SpecimenStatus.DISPATCHED);
        s.setDispatchedBy(ctx.actorId());
        s.setDispatchedAt(OffsetDateTime.now());
        appendCustody(s, SpecimenStatus.DISPATCHED, ctx.actorId(), "dispatched");
        s = specimenRepository.save(s);
        advanceOrder(loadOrder(s.getOrderId()), "DISPATCHED", "specimen dispatched: " + s.getSampleId());
        emit(s, "SPECIMEN_DISPATCHED");
        return s;
    }

    /** Receive and accession a specimen at the lab; advances the order to {@code RECEIVED_IN_LAB}. */
    @Transactional
    public SpecimenEntity receive(UUID specimenId, String labNumber) {
        TrustContext ctx = TrustContextHolder.require();
        SpecimenEntity s = load(specimenId);
        s.setStatus(SpecimenStatus.RECEIVED);
        s.setLabNumber(labNumber);
        s.setReceivedBy(ctx.actorId());
        s.setReceivedAt(OffsetDateTime.now());
        appendCustody(s, SpecimenStatus.RECEIVED, ctx.actorId(), "received in lab: " + labNumber);
        s = specimenRepository.save(s);
        advanceOrder(loadOrder(s.getOrderId()), "RECEIVED_IN_LAB", "specimen received: " + s.getSampleId());
        emit(s, "SPECIMEN_RECEIVED");
        return s;
    }

    /** Reject a specimen at receipt with a reason; advances the order to {@code SPECIMEN_REJECTED}. */
    @Transactional
    public SpecimenEntity reject(UUID specimenId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        SpecimenEntity s = load(specimenId);
        s.setStatus(SpecimenStatus.REJECTED);
        s.setRejectionReason(reason);
        appendCustody(s, SpecimenStatus.REJECTED, ctx.actorId(), "rejected: " + reason);
        s = specimenRepository.save(s);
        advanceOrder(loadOrder(s.getOrderId()), "SPECIMEN_REJECTED", "specimen rejected: " + reason);
        emit(s, "SPECIMEN_REJECTED");
        return s;
    }

    /** Mark a specimen superseded and request recollection; advances to {@code RECOLLECT_REQUESTED}. */
    @Transactional
    public SpecimenEntity requestRecollection(UUID specimenId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        SpecimenEntity s = load(specimenId);
        s.setStatus(SpecimenStatus.RECOLLECTED);
        appendCustody(s, SpecimenStatus.RECOLLECTED, ctx.actorId(), "recollection requested: " + reason);
        s = specimenRepository.save(s);
        advanceOrder(loadOrder(s.getOrderId()), "RECOLLECT_REQUESTED", "recollection requested: " + reason);
        emit(s, "SPECIMEN_RECOLLECT_REQUESTED");
        return s;
    }

    /** All specimens for an order (newest first). */
    public List<SpecimenEntity> listForOrder(String orderId) {
        return specimenRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    // ── internals ──────────────────────────────────────────────────────────

    private OrderEntity loadOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private SpecimenEntity load(UUID specimenId) {
        return specimenRepository.findById(specimenId)
                .orElseThrow(() -> new IllegalArgumentException("Specimen not found: " + specimenId));
    }

    /** Best-effort order-workflow advance: only transitions when the category guard permits it. */
    private void advanceOrder(OrderEntity order, String targetState, String reason) {
        guards.forType(order.getOrderType()).ifPresent(guard -> {
            if (guard.canTransition(order.getWorkflowState(), targetState)) {
                fulfilmentWorkflowService.transition(order.getOrderId(), targetState, reason);
            } else {
                log.debug("Specimen step skipped order advance for {}: {} -> {} not permitted",
                        order.getOrderId(), order.getWorkflowState(), targetState);
            }
        });
    }

    private String generateSampleId() {
        return "SPC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    @SuppressWarnings("unchecked")
    private void appendCustody(SpecimenEntity s, SpecimenStatus status, String actorId, String note) {
        try {
            List<Map<String, Object>> events = new ArrayList<>();
            if (s.getChainOfCustody() != null && !s.getChainOfCustody().isBlank()) {
                events = objectMapper.readValue(s.getChainOfCustody(), List.class);
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("status", status.name());
            event.put("actorId", actorId);
            event.put("note", note);
            event.put("at", OffsetDateTime.now().toString());
            events.add(event);
            s.setChainOfCustody(objectMapper.writeValueAsString(events));
        } catch (Exception e) {
            log.warn("Failed to append chain-of-custody for specimen {}: {}",
                    s.getSpecimenId(), e.getMessage());
        }
    }

    private void emit(SpecimenEntity s, String eventType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("specimenId", s.getSpecimenId() != null ? s.getSpecimenId().toString() : null);
            payload.put("orderId", s.getOrderId());
            payload.put("sampleId", s.getSampleId());
            payload.put("labNumber", s.getLabNumber());
            payload.put("status", s.getStatus().name());

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("SPECIMEN");
            event.setAggregateId(s.getSpecimenId() != null ? s.getSpecimenId().toString() : s.getOrderId());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(s.getTenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write specimen outbox event {}: {}", eventType, e.getMessage(), e);
        }
    }
}
