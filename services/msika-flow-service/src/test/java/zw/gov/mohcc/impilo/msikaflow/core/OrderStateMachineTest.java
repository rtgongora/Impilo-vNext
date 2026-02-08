package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private OrderEventRepository orderEventRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private OrderStateMachine stateMachine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine(orderRepository, orderLineRepository,
                orderEventRepository, outboxRepository, objectMapper);
    }

    @Test
    void createOrder_generatesUlidAndSetsCreatedStatus() {
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(orderEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID tenantId = UUID.randomUUID();
        OrderEntity order = stateMachine.createOrder(
                tenantId, "actor-1", ActorType.PATIENT, "cpid-123",
                OrderType.OTC_PRODUCT_ORDER, null, null, null);

        assertNotNull(order.getOrderId());
        assertEquals(26, order.getOrderId().length());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(tenantId, order.getTenantId());
        assertEquals("actor-1", order.getActorId());
        assertEquals(ActorType.PATIENT, order.getActorType());
        assertEquals("cpid-123", order.getPatientCpid());

        verify(orderRepository).save(any());
        verify(orderEventRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void createOrder_idempotencyKey_returnsExistingOrder() {
        OrderEntity existing = new OrderEntity();
        existing.setOrderId("EXISTING123456789012345");
        existing.setStatus(OrderStatus.CREATED);
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        OrderEntity result = stateMachine.createOrder(
                UUID.randomUUID(), "actor-1", ActorType.PATIENT, "cpid-123",
                OrderType.OTC_PRODUCT_ORDER, null, null, "key-1");

        assertEquals("EXISTING123456789012345", result.getOrderId());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void transition_validTransition_updatesStatusAndRecords() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.CREATED);
        order.setTenantId(UUID.randomUUID());
        order.setOrderType(OrderType.OTC_PRODUCT_ORDER);
        order.setActorId("actor-1");
        order.setAmountTotal(BigDecimal.TEN);
        order.setCurrency("ZWG");

        when(orderRepository.findById("ORDER12345678901234567")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderEntity result = stateMachine.transition(
                "ORDER12345678901234567", OrderStatus.VALIDATED,
                "actor-1", "PATIENT", "VALIDATED", null);

        assertEquals(OrderStatus.VALIDATED, result.getStatus());
        verify(orderEventRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void transition_invalidTransition_throwsException() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.CREATED);
        order.setTenantId(UUID.randomUUID());

        when(orderRepository.findById("ORDER12345678901234567")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition("ORDER12345678901234567", OrderStatus.PAID,
                        "actor-1", "PATIENT", "SKIP", null));
    }

    @Test
    void transition_completedToRefundPending_allowed() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.COMPLETED);
        order.setTenantId(UUID.randomUUID());
        order.setOrderType(OrderType.OTC_PRODUCT_ORDER);
        order.setActorId("actor-1");
        order.setAmountTotal(BigDecimal.TEN);
        order.setCurrency("ZWG");

        when(orderRepository.findById("ORDER12345678901234567")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderEntity result = stateMachine.transition(
                "ORDER12345678901234567", OrderStatus.REFUND_PENDING,
                "actor-1", "OPS", "REFUND_REQUESTED", "Customer complaint");

        assertEquals(OrderStatus.REFUND_PENDING, result.getStatus());
    }

    @Test
    void transition_terminalRefunded_noTransitionsAllowed() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.REFUNDED);
        order.setTenantId(UUID.randomUUID());

        when(orderRepository.findById("ORDER12345678901234567")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () ->
                stateMachine.transition("ORDER12345678901234567", OrderStatus.COMPLETED,
                        "actor-1", "SYSTEM", "FORCE", null));
    }

    @Test
    void transition_fullHappyPath_createdToCompleted() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setTenantId(UUID.randomUUID());
        order.setOrderType(OrderType.OTC_PRODUCT_ORDER);
        order.setActorId("actor-1");
        order.setAmountTotal(BigDecimal.TEN);
        order.setCurrency("ZWG");

        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Walk through the full happy path
        OrderStatus[] path = {
                OrderStatus.CREATED, OrderStatus.VALIDATED, OrderStatus.PRICED,
                OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, OrderStatus.ROUTED,
                OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS, OrderStatus.READY_FOR_PICKUP,
                OrderStatus.COLLECTED, OrderStatus.COMPLETED
        };

        order.setStatus(OrderStatus.CREATED);
        for (int i = 1; i < path.length; i++) {
            order.setStatus(path[i - 1]);
            stateMachine.transition("ORDER12345678901234567", path[i],
                    "actor-1", "SYSTEM", "TEST", null);
            assertEquals(path[i], order.getStatus());
        }
    }

    @Test
    void getOrder_notFound_throwsException() {
        when(orderRepository.findById("NONEXISTENT")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> stateMachine.getOrder("NONEXISTENT"));
    }
}
