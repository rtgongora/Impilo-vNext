package zw.gov.mohcc.impilo.msikaflow.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;

import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final OrderStateMachine stateMachine;

    public BookingService(OrderStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Transactional
    public OrderEntity createBooking(UUID tenantId, String actorId, ActorType actorType,
                                     String patientCpid, UUID facilityId, String idempotencyKey) {
        return stateMachine.createOrder(
                tenantId,
                actorId,
                actorType,
                patientCpid,
                OrderType.SERVICE_BOOKING_ORDER,
                facilityId,
                null,
                null,
                null,
                null,
                idempotencyKey);
    }

    @Transactional
    public OrderEntity rescheduleBooking(String orderId, String actorId, String actorType, String note) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getOrderType() != OrderType.SERVICE_BOOKING_ORDER) {
            throw new IllegalArgumentException("Not a booking order: " + orderId);
        }
        // Cancel and recreate is the pattern for reschedule
        if (order.getStatus() == OrderStatus.BOOKED || order.getStatus() == OrderStatus.ACCEPTED) {
            stateMachine.transition(orderId, OrderStatus.CANCELLED, actorId, actorType, "RESCHEDULE", note);
        }
        return order;
    }

    @Transactional
    public OrderEntity cancelBooking(String orderId, String actorId, String actorType, String reason) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getOrderType() != OrderType.SERVICE_BOOKING_ORDER) {
            throw new IllegalArgumentException("Not a booking order: " + orderId);
        }
        return stateMachine.transition(orderId, OrderStatus.CANCELLED, actorId, actorType, "BOOKING_CANCELLED", reason);
    }
}
