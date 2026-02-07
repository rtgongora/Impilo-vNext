package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;
import zw.gov.mohcc.impilo.oros.domain.AckType;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.AcknowledgementEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.AcknowledgementRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages acknowledgement workflows for clinical orders.
 *
 * <p>Supports department, clinician, and critical value acknowledgements.
 * Clinician acknowledgement of an order in RESULT_AVAILABLE status triggers
 * a transition to REVIEWED. A scheduled escalation check identifies orders
 * in RESULT_AVAILABLE status that have not been acknowledged within the
 * configured timeout and publishes ACK_ESCALATION events.</p>
 */
@Service
public class AcknowledgementService {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final AcknowledgementRepository ackRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final EventOutboxRepository outboxRepository;
    private final OrosProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the AcknowledgementService with all required dependencies.
     *
     * @param ackRepository    repository for acknowledgement persistence
     * @param orderRepository  repository for order queries
     * @param stateMachine     the order state machine for transitions
     * @param outboxRepository repository for outbox event persistence
     * @param properties       OROS configuration properties
     * @param objectMapper     Jackson mapper for JSON serialization
     */
    public AcknowledgementService(AcknowledgementRepository ackRepository,
                                  OrderRepository orderRepository,
                                  OrderStateMachine stateMachine,
                                  EventOutboxRepository outboxRepository,
                                  OrosProperties properties,
                                  ObjectMapper objectMapper) {
        this.ackRepository = ackRepository;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Record an acknowledgement for an order.
     *
     * <p>If the acknowledgement type is CLINICIAN and the order is in
     * RESULT_AVAILABLE status, the order is transitioned to REVIEWED.</p>
     *
     * @param orderId the ULID order identifier
     * @param ackType the type of acknowledgement (DEPARTMENT, CLINICIAN, CRITICAL)
     * @param notes   optional notes accompanying the acknowledgement
     * @return the created acknowledgement entity
     */
    @Transactional
    public AcknowledgementEntity acknowledge(String orderId, AckType ackType, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);

        AcknowledgementEntity ack = new AcknowledgementEntity();
        ack.setAckId(UUID.randomUUID());
        ack.setOrderId(orderId);
        ack.setAckType(ackType);
        ack.setActorId(ctx.actorId());
        ack.setNotes(notes);
        ack.setAckedAt(OffsetDateTime.now());
        ack = ackRepository.save(ack);

        // CLINICIAN ack transitions order to REVIEWED if in RESULT_AVAILABLE
        if (ackType == AckType.CLINICIAN && order.getStatus() == OrderStatus.RESULT_AVAILABLE) {
            stateMachine.transition(order, OrderStatus.REVIEWED);
            log.info("Order {} transitioned to REVIEWED after clinician acknowledgement", orderId);
        }

        // Publish ACK_RECEIVED event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("ackType", ackType.name());
        payload.put("actorId", ctx.actorId());
        payload.put("ackedAt", ack.getAckedAt().toString());
        publishEvent("ACK", orderId, "ACK_RECEIVED", payload, ctx.tenantId());

        log.info("Order acknowledged: orderId={}, ackType={}, actor={}",
                orderId, ackType, ctx.actorId());
        return ack;
    }

    /**
     * Returns all acknowledgements for the specified order.
     *
     * @param orderId the ULID order identifier
     * @return list of acknowledgement entities
     */
    public List<AcknowledgementEntity> getAcks(String orderId) {
        return ackRepository.findByOrderId(orderId);
    }

    /**
     * Scheduled escalation check for unacknowledged orders.
     *
     * <p>Finds orders in RESULT_AVAILABLE status that have been waiting longer than
     * the configured acknowledgement timeout without a CLINICIAN acknowledgement.
     * For each such order, publishes an ACK_ESCALATION outbox event to alert
     * supervisors and clinical leads.</p>
     *
     * <p>This method is idempotent: orders that have already triggered an escalation
     * will be checked again, but downstream consumers should deduplicate.</p>
     */
    @Scheduled(fixedDelayString = "${oros.escalation.check-interval-ms:120000}")
    @Transactional
    public void checkEscalation() {
        int timeoutMinutes = properties.getEscalation().getAckTimeoutMinutes();
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(timeoutMinutes);

        // Find all orders in RESULT_AVAILABLE status
        // We check each order's updatedAt against the cutoff to find overdue ones
        List<OrderEntity> candidates = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.RESULT_AVAILABLE)
                .filter(o -> o.getUpdatedAt() != null && o.getUpdatedAt().isBefore(cutoff))
                .toList();

        int escalated = 0;
        for (OrderEntity order : candidates) {
            // Check if CLINICIAN ack already exists
            boolean hasClinAck = ackRepository.existsByOrderIdAndAckType(
                    order.getOrderId(), AckType.CLINICIAN);

            if (hasClinAck) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.getOrderId());
            payload.put("tenantId", order.getTenantId().toString());
            payload.put("facilityId", order.getFacilityId().toString());
            payload.put("patientCpid", order.getPatientCpid());
            payload.put("orderType", order.getOrderType().name());
            payload.put("placedBy", order.getPlacedBy());
            payload.put("resultAvailableSince", order.getUpdatedAt().toString());
            payload.put("timeoutMinutes", timeoutMinutes);
            payload.put("escalatedAt", OffsetDateTime.now().toString());
            publishEvent("ACK", order.getOrderId(), "ACK_ESCALATION", payload, order.getTenantId());

            escalated++;
        }

        if (escalated > 0) {
            log.warn("Acknowledgement escalation: {} orders overdue for clinician acknowledgement", escalated);
        }
    }

    /**
     * Publishes an event to the outbox table.
     */
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
