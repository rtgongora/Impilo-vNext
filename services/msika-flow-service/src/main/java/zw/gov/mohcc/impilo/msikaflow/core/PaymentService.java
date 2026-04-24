package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderStateMachine stateMachine;
    private final SettlementRepository settlementRepository;
    private final RefundRepository refundRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(OrderStateMachine stateMachine,
                          SettlementRepository settlementRepository,
                          RefundRepository refundRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.settlementRepository = settlementRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SettlementEntity createPaymentIntent(String orderId, String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.PRICED) {
            throw new IllegalStateException("Order must be PRICED to create payment intent. Current: " + order.getStatus());
        }

        // Transition to PAYMENT_PENDING
        stateMachine.transition(orderId, OrderStatus.PAYMENT_PENDING, actorId, actorType, "PAYMENT_INTENT_CREATED", null);

        // Create settlement record with MUSHEX payment intent (simulated ID)
        String mushexPaymentIntentId = "mpi_" + UlidGenerator.generate();

        SettlementEntity settlement = new SettlementEntity();
        settlement.setId(UlidGenerator.generate());
        settlement.setOrderId(orderId);
        settlement.setMushexPaymentIntentId(mushexPaymentIntentId);
        settlement.setStatus(SettlementStatus.PENDING);

        try {
            Map<String, Object> splits = new LinkedHashMap<>();
            splits.put("vendorShare", order.getAmountTotal().multiply(new BigDecimal("0.90")).toPlainString());
            splits.put("platformFee", order.getAmountTotal().multiply(new BigDecimal("0.10")).toPlainString());
            splits.put("currency", order.getCurrency());
            settlement.setSplits(objectMapper.writeValueAsString(splits));
        } catch (Exception e) {
            log.warn("Failed to serialize splits: {}", e.getMessage());
        }

        settlementRepository.save(settlement);

        log.info("Payment intent created: orderId={} mushexId={}", orderId, mushexPaymentIntentId);
        return settlement;
    }

    @Transactional
    public void handlePaymentCallback(String mushexPaymentIntentId, String status, String actorId) {
        SettlementEntity settlement = settlementRepository.findByMushexPaymentIntentId(mushexPaymentIntentId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found for MUSHEX ID: " + mushexPaymentIntentId));

        OrderEntity order = stateMachine.getOrder(settlement.getOrderId());

        if ("PAID".equalsIgnoreCase(status)) {
            if (order.getStatus() == OrderStatus.PAID) {
                log.debug("Duplicate PAID callback for order {}; ignoring state transition", settlement.getOrderId());
                return;
            }
            stateMachine.transition(settlement.getOrderId(), OrderStatus.PAID, actorId, "SYSTEM", "PAYMENT_CONFIRMED", null);
            settlement.setStatus(SettlementStatus.SPLIT_CALCULATED);
            settlementRepository.save(settlement);
            log.info("Payment confirmed: orderId={}", settlement.getOrderId());
        } else if ("FAILED".equalsIgnoreCase(status)) {
            if (order.getStatus() == OrderStatus.FAILED) {
                log.debug("Duplicate FAILED callback for order {}; ignoring", settlement.getOrderId());
                return;
            }
            stateMachine.transition(settlement.getOrderId(), OrderStatus.FAILED, actorId, "SYSTEM", "PAYMENT_FAILED", null);
            settlement.setStatus(SettlementStatus.FAILED);
            settlementRepository.save(settlement);
            log.warn("Payment failed: orderId={}", settlement.getOrderId());
        }
    }

    @Transactional
    public RefundEntity requestRefund(String orderId, BigDecimal amount, String reason,
                                      String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);

        // Validate refund eligibility
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.CANCELLED
                && order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Cannot refund order in status: " + order.getStatus());
        }

        if (amount.compareTo(order.getAmountTotal()) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds order total");
        }

        RefundEntity refund = new RefundEntity();
        refund.setId(UlidGenerator.generate());
        refund.setOrderId(orderId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.REQUESTED);

        refundRepository.save(refund);

        // Transition to REFUND_PENDING
        if (!order.getStatus().isTerminal() || order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            stateMachine.transition(orderId, OrderStatus.REFUND_PENDING, actorId, actorType, "REFUND_REQUESTED", reason);
        }

        publishOutbox("Refund", refund.getId(), "REFUND_REQUESTED", order.getTenantId(),
                Map.of("orderId", orderId, "amount", amount.toPlainString(), "reason", reason));

        log.info("Refund requested: orderId={} amount={}", orderId, amount);
        return refund;
    }

    @Transactional
    public void completeRefund(String refundId, String mushexRefundId) {
        RefundEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));

        refund.setStatus(RefundStatus.COMPLETED);
        refund.setMushexRefundId(mushexRefundId);
        refundRepository.save(refund);

        OrderEntity order = stateMachine.getOrder(refund.getOrderId());
        stateMachine.transition(refund.getOrderId(), OrderStatus.REFUNDED,
                "SYSTEM", "SYSTEM", "REFUND_COMPLETED", null);

        publishOutbox("Refund", refundId, "REFUND_COMPLETED", order.getTenantId(),
                Map.of("orderId", refund.getOrderId(), "mushexRefundId", mushexRefundId));

        log.info("Refund completed: orderId={} mushexRefundId={}", refund.getOrderId(), mushexRefundId);
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               UUID tenantId, Map<String, Object> data) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(data));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox: {}", e.getMessage());
        }
    }
}
