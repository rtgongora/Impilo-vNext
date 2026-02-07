package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.AckType;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.AcknowledgementEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.AcknowledgementRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

/**
 * Provides worklist operations for order acceptance and rejection.
 *
 * <p>The worklist is a filtered view of orders at a facility, scoped by
 * order type, status, and priority. Supports priority-based sorting
 * where STAT > URGENT > ASAP > ROUTINE.</p>
 */
@Service
public class WorklistService {

    private static final Logger log = LoggerFactory.getLogger(WorklistService.class);

    private final OrderRepository orderRepository;
    private final AcknowledgementRepository ackRepository;
    private final EventOutboxRepository outboxRepository;
    private final OrderStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    public WorklistService(OrderRepository orderRepository,
                           AcknowledgementRepository ackRepository,
                           EventOutboxRepository outboxRepository,
                           OrderStateMachine stateMachine,
                           ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.ackRepository = ackRepository;
        this.outboxRepository = outboxRepository;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the filtered worklist for a facility.
     * Orders are sorted by priority (STAT first) then by placement time.
     */
    public Page<OrderEntity> getWorklist(UUID facilityId, UUID workspaceId,
                                         OrderType type, OrderStatus status,
                                         OrderPriority priority, Pageable pageable) {
        // Apply priority-based sorting: STAT > URGENT > ASAP > ROUTINE, then by placedAt
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("placedAt")));

        if (type != null && status != null && priority != null) {
            if (workspaceId != null) {
                return orderRepository.findByFacilityIdAndWorkspaceIdAndOrderTypeAndStatusAndPriority(
                        facilityId, workspaceId, type, status, priority, sorted);
            }
            return orderRepository.findByFacilityIdAndOrderTypeAndStatusAndPriority(
                    facilityId, type, status, priority, sorted);
        }
        if (type != null && status != null) {
            return orderRepository.findByFacilityIdAndOrderTypeAndStatus(
                    facilityId, type, status, sorted);
        }
        if (type != null) {
            return orderRepository.findByFacilityIdAndOrderType(facilityId, type, sorted);
        }
        if (status != null) {
            return orderRepository.findByFacilityIdAndStatus(facilityId, status, sorted);
        }
        return orderRepository.findByFacilityId(facilityId, sorted);
    }

    /**
     * Accept an order. Transitions to ACCEPTED and creates a DEPARTMENT acknowledgement.
     */
    @Transactional
    public OrderEntity acceptOrder(String orderId) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);

        order = stateMachine.transition(order, OrderStatus.ACCEPTED);

        // Create department acknowledgement
        AcknowledgementEntity ack = new AcknowledgementEntity();
        ack.setOrderId(orderId);
        ack.setAckType(AckType.DEPARTMENT);
        ack.setActorId(ctx.actorId());
        ack.setNotes("Order accepted");
        ackRepository.save(ack);

        log.info("Order accepted: orderId={}, actor={}", orderId, ctx.actorId());
        return order;
    }

    /**
     * Reject an order with a reason. Transitions to REJECTED.
     */
    @Transactional
    public OrderEntity rejectOrder(String orderId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        OrderEntity order = stateMachine.getOrder(orderId);

        order.setClinicalNotes(
                (order.getClinicalNotes() != null ? order.getClinicalNotes() + "\n" : "")
                        + "REJECTION: " + reason);

        order = stateMachine.transition(order, OrderStatus.REJECTED);

        log.info("Order rejected: orderId={}, reason={}, actor={}", orderId, reason, ctx.actorId());
        return order;
    }
}
