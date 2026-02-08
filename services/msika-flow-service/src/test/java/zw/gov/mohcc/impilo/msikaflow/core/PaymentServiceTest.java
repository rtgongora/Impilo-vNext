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
class PaymentServiceTest {

    @Mock private OrderStateMachine stateMachine;
    @Mock private SettlementRepository settlementRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private PaymentService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PaymentService(stateMachine, settlementRepository, refundRepository, outboxRepository, objectMapper);
    }

    @Test
    void createPaymentIntent_pricedOrder_createsSettlement() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PRICED);
        order.setTenantId(UUID.randomUUID());
        order.setAmountTotal(new BigDecimal("25.00"));
        order.setCurrency("ZWG");

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(settlementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SettlementEntity settlement = service.createPaymentIntent("ORDER12345678901234567", "actor-1", "PATIENT");

        assertNotNull(settlement.getId());
        assertNotNull(settlement.getMushexPaymentIntentId());
        assertEquals(SettlementStatus.PENDING, settlement.getStatus());

        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.PAYMENT_PENDING, "actor-1", "PATIENT", "PAYMENT_INTENT_CREATED", null);
    }

    @Test
    void createPaymentIntent_wrongStatus_throws() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.CREATED);

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        assertThrows(IllegalStateException.class, () ->
                service.createPaymentIntent("ORDER12345678901234567", "actor-1", "PATIENT"));
    }

    @Test
    void handlePaymentCallback_paid_transitionsOrder() {
        SettlementEntity settlement = new SettlementEntity();
        settlement.setId("SETTLE1234567890123456");
        settlement.setOrderId("ORDER12345678901234567");
        settlement.setStatus(SettlementStatus.PENDING);

        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        when(settlementRepository.findByMushexPaymentIntentId("mpi_123")).thenReturn(Optional.of(settlement));
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(settlementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.handlePaymentCallback("mpi_123", "PAID", "SYSTEM");

        assertEquals(SettlementStatus.SPLIT_CALCULATED, settlement.getStatus());
        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.PAID, "SYSTEM", "SYSTEM", "PAYMENT_CONFIRMED", null);
    }

    @Test
    void requestRefund_exceedsTotal_throws() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.COMPLETED);
        order.setAmountTotal(new BigDecimal("25.00"));

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () ->
                service.requestRefund("ORDER12345678901234567", new BigDecimal("50.00"),
                        "Too much", "actor-1", "OPS"));
    }

    @Test
    void requestRefund_validAmount_createsRefund() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.COMPLETED);
        order.setTenantId(UUID.randomUUID());
        order.setAmountTotal(new BigDecimal("25.00"));

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundEntity refund = service.requestRefund("ORDER12345678901234567", new BigDecimal("10.00"),
                "Partial refund", "actor-1", "OPS");

        assertNotNull(refund.getId());
        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(new BigDecimal("10.00"), refund.getAmount());
    }
}
