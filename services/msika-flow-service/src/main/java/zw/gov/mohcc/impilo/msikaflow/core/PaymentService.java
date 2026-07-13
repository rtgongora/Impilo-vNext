package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.config.MsikaFlowProperties;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.MushexClient;
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
    private final MushexClient mushexClient;
    private final MsikaFlowProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentService(OrderStateMachine stateMachine,
                          SettlementRepository settlementRepository,
                          RefundRepository refundRepository,
                          EventOutboxRepository outboxRepository,
                          MushexClient mushexClient,
                          MsikaFlowProperties properties,
                          ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.settlementRepository = settlementRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;
        this.mushexClient = mushexClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Real money leg: create a MusheX payment intent over HTTP (COSTA's synchronous
     * handoff precedent) and persist the REAL intent id on {@code mf_settlements}.
     *
     * <p>Fail-loud: the MusheX call happens before any state change. If MusheX rejects
     * or is unreachable the exception propagates, the order stays PRICED and no
     * settlement row is written — a settlement with a fabricated intent id is exactly
     * the dormant state this replaces.</p>
     */
    @Transactional
    public SettlementEntity createPaymentIntent(String orderId, String actorId, String actorType,
                                                HttpServletRequest inboundRequest) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.PRICED) {
            throw new IllegalStateException("Order must be PRICED to create payment intent. Current: " + order.getStatus());
        }

        Map<String, Object> splits = computeSplits(order);
        String metadataJson = buildIntentMetadata(order, splits);

        MushexClient.MushexIntentCreated intent = mushexClient.createPaymentIntent(
                inboundRequest,
                orderId,
                order.getAmountTotal(),
                order.getCurrency(),
                order.getFacilityId() != null ? order.getFacilityId().toString() : null,
                metadataJson);

        stateMachine.transition(orderId, OrderStatus.PAYMENT_PENDING, actorId, actorType, "PAYMENT_INTENT_CREATED", null);

        SettlementEntity settlement = new SettlementEntity();
        settlement.setId(UlidGenerator.generate());
        settlement.setOrderId(orderId);
        settlement.setMushexPaymentIntentId(intent.intentId());
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setSplits(toJsonSafe(splits));
        settlement.setCostaChargeRef(readCostaChargeRef(order));
        settlementRepository.save(settlement);

        log.info("Payment intent created via MusheX: orderId={} intentId={}", orderId, intent.intentId());
        return settlement;
    }

    /**
     * Splits are derived from the order's price snapshot (vendor gets the goods
     * subtotal + delivery, platform keeps the platform fee) — replacing the previous
     * hardcoded 90/10 split that contradicted the 1.5% platform fee actually priced.
     * Falls back to {@code msika-flow.pricing.*} recomputation when the snapshot is
     * absent (legacy orders).
     */
    private Map<String, Object> computeSplits(OrderEntity order) {
        BigDecimal total = order.getAmountTotal();
        BigDecimal platformFee = null;
        BigDecimal vendorShare = null;
        BigDecimal deliveryFee = BigDecimal.ZERO;

        if (order.getPriceSnapshot() != null) {
            try {
                JsonNode snap = objectMapper.readTree(order.getPriceSnapshot());
                BigDecimal subtotal = readDecimal(snap, "subtotal");
                platformFee = readDecimal(snap, "platformFee");
                BigDecimal snapDelivery = readDecimal(snap, "deliveryFee");
                if (snapDelivery != null) {
                    deliveryFee = snapDelivery;
                }
                if (subtotal != null) {
                    vendorShare = subtotal.add(deliveryFee);
                }
            } catch (Exception e) {
                log.warn("Unparseable price snapshot for order {}: {}", order.getOrderId(), e.getMessage());
            }
        }
        if (platformFee == null || vendorShare == null) {
            BigDecimal rate = new BigDecimal(properties.getPricing().getPlatformFeeRate());
            BigDecimal cap = new BigDecimal(properties.getPricing().getPlatformFeeCap());
            platformFee = total.multiply(rate).min(cap);
            vendorShare = total.subtract(platformFee);
        }

        Map<String, Object> splits = new LinkedHashMap<>();
        splits.put("vendorShare", vendorShare.toPlainString());
        splits.put("platformFee", platformFee.toPlainString());
        splits.put("deliveryFee", deliveryFee.toPlainString());
        splits.put("total", total.toPlainString());
        splits.put("currency", order.getCurrency());
        return splits;
    }

    private String buildIntentMetadata(OrderEntity order, Map<String, Object> splits) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("order_id", order.getOrderId());
        // The payee's share of this intent (vendor goods + delivery, excluding the
        // platform fee). MusheX settlement aggregates payouts from THIS figure so
        // a vendor is never paid the platform fee by mistake.
        Object vendorShare = splits.get("vendorShare");
        if (vendorShare != null) {
            metadata.put("payee_amount", vendorShare.toString());
        }
        if (order.getPatientCpid() != null && !order.getPatientCpid().isBlank()) {
            metadata.put("patient_cpid", order.getPatientCpid());
        }
        metadata.put("order_type", order.getOrderType().name());
        if (order.getVendorId() != null) {
            metadata.put("provider_id", "vendor:" + order.getVendorId());
        } else if (order.getVendorRef() != null && !order.getVendorRef().isBlank()) {
            // Marketplace seller id (string) — the payee for a citizen-buyer order.
            metadata.put("provider_id", "vendor:" + order.getVendorRef());
        } else if (order.getFacilityId() != null) {
            metadata.put("provider_id", "facility:" + order.getFacilityId());
        }
        if (properties.getPayments().isSimulationMetadata()) {
            // Rig/preview escape hatch — must remain false in production. MusheX
            // keys off "impilo_simulation" (to skip the provider-credential gate)
            // and "simulation_outcome" (to settle the sandbox intent); emit both
            // so the escape hatch actually engages.
            metadata.put("impilo_simulation", true);
            metadata.put("simulation_outcome", "paid");
        }
        return toJsonSafe(metadata);
    }

    /** CostaChargeEventConsumer stashes the charge ref on the price snapshot when the
     *  charge event lands before the settlement exists (the normal ordering). */
    private String readCostaChargeRef(OrderEntity order) {
        if (order.getPriceSnapshot() == null) {
            return null;
        }
        try {
            JsonNode snap = objectMapper.readTree(order.getPriceSnapshot());
            return snap.hasNonNull("costaChargeRef") ? snap.get("costaChargeRef").asText() : null;
        } catch (Exception e) {
            return null;
        }
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

    /**
     * Refund execution is real: the MusheX refund is requested over HTTP first
     * (step-up header propagates via the client), and only then is the local refund
     * row written with the REAL {@code mushex_refund_id} in PENDING_EXECUTION.
     * Completion/failure arrives asynchronously via
     * {@code mushex.refund.status.changed} → {@code MushexRefundEventConsumer}.
     */
    @Transactional
    public RefundEntity requestRefund(String orderId, BigDecimal amount, String reason,
                                      String actorId, String actorType,
                                      HttpServletRequest inboundRequest) {
        OrderEntity order = stateMachine.getOrder(orderId);

        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.CANCELLED
                && order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Cannot refund order in status: " + order.getStatus());
        }

        if (amount.compareTo(order.getAmountTotal()) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds order total");
        }

        SettlementEntity settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No settlement/payment intent exists for order " + orderId + " — nothing to refund"));

        MushexClient.MushexRefundCreated mushexRefund = mushexClient.requestRefund(
                inboundRequest, settlement.getMushexPaymentIntentId(), amount, reason);

        RefundEntity refund = new RefundEntity();
        refund.setId(UlidGenerator.generate());
        refund.setOrderId(orderId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING_EXECUTION);
        refund.setMushexRefundId(mushexRefund.refundId());
        refundRepository.save(refund);

        stateMachine.transition(orderId, OrderStatus.REFUND_PENDING, actorId, actorType, "REFUND_REQUESTED", reason);

        publishOutbox("Refund", refund.getId(), "REFUND_REQUESTED", order.getTenantId(),
                Map.of("orderId", orderId,
                        "tenantId", order.getTenantId().toString(),
                        "refundId", refund.getId(),
                        "mushexRefundId", mushexRefund.refundId(),
                        "amount", amount.toPlainString(),
                        "reason", reason != null ? reason : ""));

        log.info("Refund requested via MusheX: orderId={} amount={} mushexRefundId={}",
                orderId, amount, mushexRefund.refundId());
        return refund;
    }

    /**
     * Terminal leg of the refund loop, driven by {@code mushex.refund.status.changed}.
     * Returns {@code false} when the MusheX refund id does not belong to msika-flow
     * (the topic also carries COSTA-bill refunds).
     */
    @Transactional
    public boolean completeRefundByMushexId(String mushexRefundId) {
        Optional<RefundEntity> maybe = refundRepository.findByMushexRefundId(mushexRefundId);
        if (maybe.isEmpty()) {
            return false;
        }
        RefundEntity refund = maybe.get();
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return true;
        }
        refund.setStatus(RefundStatus.COMPLETED);
        refundRepository.save(refund);

        OrderEntity order = stateMachine.getOrder(refund.getOrderId());
        if (order.getStatus() != OrderStatus.REFUNDED) {
            stateMachine.transition(refund.getOrderId(), OrderStatus.REFUNDED,
                    "SYSTEM", "SYSTEM", "REFUND_COMPLETED", null);
        }

        publishOutbox("Refund", refund.getId(), "REFUND_COMPLETED", order.getTenantId(),
                Map.of("orderId", refund.getOrderId(),
                        "tenantId", order.getTenantId().toString(),
                        "refundId", refund.getId(),
                        "mushexRefundId", mushexRefundId,
                        "amount", refund.getAmount().toPlainString()));

        log.info("Refund completed: orderId={} mushexRefundId={}", refund.getOrderId(), mushexRefundId);
        return true;
    }

    /** Honest failure path: MusheX said no — the refund is FAILED, not silently retried. */
    @Transactional
    public boolean failRefundByMushexId(String mushexRefundId, String reason) {
        Optional<RefundEntity> maybe = refundRepository.findByMushexRefundId(mushexRefundId);
        if (maybe.isEmpty()) {
            return false;
        }
        RefundEntity refund = maybe.get();
        if (refund.getStatus() == RefundStatus.FAILED || refund.getStatus() == RefundStatus.COMPLETED) {
            return true;
        }
        refund.setStatus(RefundStatus.FAILED);
        refundRepository.save(refund);

        OrderEntity order = stateMachine.getOrder(refund.getOrderId());
        if (order.getStatus() == OrderStatus.REFUND_PENDING) {
            stateMachine.transition(refund.getOrderId(), OrderStatus.FAILED,
                    "SYSTEM", "SYSTEM", "REFUND_FAILED", reason);
        }

        publishOutbox("Refund", refund.getId(), "REFUND_FAILED", order.getTenantId(),
                Map.of("orderId", refund.getOrderId(),
                        "tenantId", order.getTenantId().toString(),
                        "refundId", refund.getId(),
                        "mushexRefundId", mushexRefundId,
                        "reason", reason != null ? reason : ""));

        log.warn("Refund failed: orderId={} mushexRefundId={} reason={}",
                refund.getOrderId(), mushexRefundId, reason);
        return true;
    }

    private String toJsonSafe(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize payload: {}", e.getMessage());
            return "{}";
        }
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

    private static BigDecimal readDecimal(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
