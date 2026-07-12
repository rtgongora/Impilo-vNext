package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.config.MsikaFlowProperties;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.MushexClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private OrderStateMachine stateMachine;
    @Mock private SettlementRepository settlementRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private MushexClient mushexClient;
    @Mock private HttpServletRequest inbound;

    private PaymentService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PaymentService(stateMachine, settlementRepository, refundRepository,
                outboxRepository, mushexClient, new MsikaFlowProperties(), objectMapper);
    }

    private OrderEntity pricedOrder() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PRICED);
        order.setTenantId(UUID.randomUUID());
        order.setOrderType(OrderType.OTC_PRODUCT_ORDER);
        order.setAmountTotal(new BigDecimal("25.00"));
        order.setCurrency("ZWG");
        return order;
    }

    @Test
    void createPaymentIntent_pricedOrder_persistsRealIntentId() {
        OrderEntity order = pricedOrder();
        order.setPriceSnapshot("{\"subtotal\":\"20.00\",\"platformFee\":\"0.30\",\"deliveryFee\":\"0.00\",\"total\":\"20.30\"}");

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(mushexClient.createPaymentIntent(any(), any(), any(), any(), any(), any()))
                .thenReturn(new MushexClient.MushexIntentCreated("mpi_REAL_123", "CREATED"));
        when(settlementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SettlementEntity settlement = service.createPaymentIntent("ORDER12345678901234567", "actor-1", "PATIENT", inbound);

        assertEquals("mpi_REAL_123", settlement.getMushexPaymentIntentId());
        assertEquals(SettlementStatus.PENDING, settlement.getStatus());
        // Splits derived from the snapshot, not a hardcoded 90/10.
        assertTrue(settlement.getSplits().contains("\"vendorShare\":\"20.00\""));
        assertTrue(settlement.getSplits().contains("\"platformFee\":\"0.30\""));
        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.PAYMENT_PENDING,
                "actor-1", "PATIENT", "PAYMENT_INTENT_CREATED", null);
    }

    @Test
    void createPaymentIntent_mushexFails_failsLoud_noSettlementPersisted() {
        OrderEntity order = pricedOrder();
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(mushexClient.createPaymentIntent(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("MusheX payment intent create failed: HTTP 502"));

        assertThrows(IllegalStateException.class, () ->
                service.createPaymentIntent("ORDER12345678901234567", "actor-1", "PATIENT", inbound));

        verify(settlementRepository, never()).save(any());
        verify(stateMachine, never()).transition(any(), eq(OrderStatus.PAYMENT_PENDING), any(), any(), any(), any());
    }

    @Test
    void createPaymentIntent_wrongStatus_throws() {
        OrderEntity order = pricedOrder();
        order.setStatus(OrderStatus.CREATED);
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        assertThrows(IllegalStateException.class, () ->
                service.createPaymentIntent("ORDER12345678901234567", "actor-1", "PATIENT", inbound));
        verify(mushexClient, never()).createPaymentIntent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handlePaymentCallback_paid_transitionsOrder() {
        SettlementEntity settlement = new SettlementEntity();
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
    void handlePaymentCallback_duplicatePaid_doesNotTransitionAgain() {
        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId("ORDER12345678901234567");
        settlement.setStatus(SettlementStatus.SPLIT_CALCULATED);

        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PAID);

        when(settlementRepository.findByMushexPaymentIntentId("mpi_123")).thenReturn(Optional.of(settlement));
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);

        service.handlePaymentCallback("mpi_123", "PAID", "SYSTEM");

        verify(stateMachine, never()).transition(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestRefund_callsMushex_storesRefundIdPendingExecution() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PAID);
        order.setTenantId(UUID.randomUUID());
        order.setAmountTotal(new BigDecimal("25.00"));

        SettlementEntity settlement = new SettlementEntity();
        settlement.setOrderId("ORDER12345678901234567");
        settlement.setMushexPaymentIntentId("mpi_REAL_123");

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(settlementRepository.findByOrderId("ORDER12345678901234567")).thenReturn(Optional.of(settlement));
        when(mushexClient.requestRefund(any(), eq("mpi_REAL_123"), any(), any()))
                .thenReturn(new MushexClient.MushexRefundCreated("mrf_REAL_9", "PENDING"));
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RefundEntity refund = service.requestRefund("ORDER12345678901234567", new BigDecimal("10.00"),
                "Partial refund", "actor-1", "OPS", inbound);

        assertEquals("mrf_REAL_9", refund.getMushexRefundId());
        assertEquals(RefundStatus.PENDING_EXECUTION, refund.getStatus());
        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.REFUND_PENDING,
                "actor-1", "OPS", "REFUND_REQUESTED", "Partial refund");
    }

    @Test
    void requestRefund_noSettlement_throws() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.PAID);
        order.setAmountTotal(new BigDecimal("25.00"));

        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(settlementRepository.findByOrderId("ORDER12345678901234567")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                service.requestRefund("ORDER12345678901234567", new BigDecimal("10.00"),
                        "x", "actor-1", "OPS", inbound));
        verify(mushexClient, never()).requestRefund(any(), any(), any(), any());
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
                        "Too much", "actor-1", "OPS", inbound));
    }

    @Test
    void completeRefundByMushexId_marksOrderRefunded() {
        RefundEntity refund = new RefundEntity();
        refund.setId("REFUND1234567890123456");
        refund.setOrderId("ORDER12345678901234567");
        refund.setAmount(new BigDecimal("10.00"));
        refund.setStatus(RefundStatus.PENDING_EXECUTION);
        refund.setMushexRefundId("mrf_REAL_9");

        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.REFUND_PENDING);
        order.setTenantId(UUID.randomUUID());

        when(refundRepository.findByMushexRefundId("mrf_REAL_9")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.completeRefundByMushexId("mrf_REAL_9"));
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.REFUNDED,
                "SYSTEM", "SYSTEM", "REFUND_COMPLETED", null);
    }

    @Test
    void completeRefundByMushexId_unknownId_returnsFalse() {
        when(refundRepository.findByMushexRefundId("mrf_costa")).thenReturn(Optional.empty());
        assertFalse(service.completeRefundByMushexId("mrf_costa"));
        verify(stateMachine, never()).transition(any(), any(), any(), any(), any(), any());
    }

    @Test
    void failRefundByMushexId_marksOrderFailed() {
        RefundEntity refund = new RefundEntity();
        refund.setId("REFUND1234567890123456");
        refund.setOrderId("ORDER12345678901234567");
        refund.setStatus(RefundStatus.PENDING_EXECUTION);
        refund.setMushexRefundId("mrf_REAL_9");

        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.REFUND_PENDING);
        order.setTenantId(UUID.randomUUID());

        when(refundRepository.findByMushexRefundId("mrf_REAL_9")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stateMachine.getOrder("ORDER12345678901234567")).thenReturn(order);
        when(stateMachine.transition(any(), any(), any(), any(), any(), any())).thenReturn(order);
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.failRefundByMushexId("mrf_REAL_9", "rail declined"));
        assertEquals(RefundStatus.FAILED, refund.getStatus());
        verify(stateMachine).transition("ORDER12345678901234567", OrderStatus.FAILED,
                "SYSTEM", "SYSTEM", "REFUND_FAILED", "rail declined");
    }
}
