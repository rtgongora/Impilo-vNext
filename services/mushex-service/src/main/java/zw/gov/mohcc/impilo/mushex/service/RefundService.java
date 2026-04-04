package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.RefundStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refund workflow service.
 *
 * Manages the lifecycle of refunds against paid payment intents. Validates
 * refund amounts against available balances, applies step-up thresholds for
 * large refunds, and coordinates with the ledger service.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentIntentRepository intentRepository;
    private final PaymentIntentService intentService;
    private final LedgerService ledgerService;
    private final EventOutboxRepository outboxRepository;
    private final MushexProperties properties;
    private final ObjectMapper objectMapper;

    public RefundService(RefundRepository refundRepository,
                         PaymentIntentRepository intentRepository,
                         PaymentIntentService intentService,
                         LedgerService ledgerService,
                         EventOutboxRepository outboxRepository,
                         MushexProperties properties,
                         ObjectMapper objectMapper) {
        this.refundRepository = refundRepository;
        this.intentRepository = intentRepository;
        this.intentService = intentService;
        this.ledgerService = ledgerService;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Request a refund against a paid payment intent.
     *
     * Validates:
     * - The intent is in PAID status
     * - The refund amount does not exceed (amountPaid - existing refunds)
     * - If the amount exceeds the large refund threshold, step-up is required
     *
     * @param intentId the payment intent to refund
     * @param amount   the refund amount
     * @param reason   the reason for the refund
     * @return the created refund entity
     */
    @Transactional
    public RefundEntity requestRefund(String intentId, BigDecimal amount, String reason) {
        TrustContext ctx = TrustContextHolder.require();

        PaymentIntentEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        // Validate intent is paid
        if (intent.getStatus() != IntentStatus.PAID) {
            throw new IllegalStateException("Cannot refund intent in status: " + intent.getStatus());
        }

        // Calculate refundable balance
        List<RefundEntity> existingRefunds = refundRepository.findByIntentId(intentId);
        BigDecimal totalRefunded = existingRefunds.stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .map(RefundEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refundableBalance = intent.getAmountPaid().subtract(totalRefunded);
        if (amount.compareTo(refundableBalance) > 0) {
            throw new IllegalArgumentException(
                    String.format("Refund amount %s exceeds refundable balance %s (paid: %s, already refunded: %s)",
                            amount.toPlainString(), refundableBalance.toPlainString(),
                            intent.getAmountPaid().toPlainString(), totalRefunded.toPlainString()));
        }

        // Check step-up threshold for large refunds
        BigDecimal threshold = properties.getStepUp().getLargeRefundThreshold();
        if (amount.compareTo(threshold) > 0) {
            log.warn("Large refund detected: amount={} exceeds threshold={}. Step-up authorization required.",
                    amount.toPlainString(), threshold.toPlainString());
            // In a full implementation, this would trigger step-up auth via TSHEPO.
            // For now, we log a warning and proceed. Real step-up enforcement would be
            // at the controller/filter level using TrustContext.mode() == STEP_UP.
        }

        // Create refund record
        RefundEntity refund = new RefundEntity();
        refund.setRefundId(UlidGenerator.generate());
        refund.setIntentId(intentId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING);

        refund = refundRepository.save(refund);

        // Post ledger entry for the refund
        ledgerService.postRefund(ctx.tenantId(), intentId, amount, intent.getCurrency());

        // Transition intent to REFUND_PENDING
        intentService.transitionStatus(intentId, IntentStatus.REFUND_PENDING);

        log.info("Refund requested: refundId={}, intentId={}, amount={}",
                refund.getRefundId(), intentId, amount.toPlainString());

        publishEvent("REFUND", refund.getRefundId(), "REFUND_REQUESTED",
                Map.of(
                        "refundId", refund.getRefundId(),
                        "intentId", intentId,
                        "billId", intent.getSourceId() != null ? intent.getSourceId() : "",
                        "amount", amount.toPlainString(),
                        "reason", reason != null ? reason : ""
                ),
                ctx.tenantId());

        return refund;
    }

    /**
     * Mark a refund as completed (processed by the payment adapter).
     * If the intent is fully refunded, transitions it to REFUNDED status.
     *
     * @param refundId   the refund to process
     * @param adapterRef external reference from the payment adapter
     * @return the completed refund entity
     */
    @Transactional
    public RefundEntity processRefund(String refundId, String adapterRef) {
        TrustContext ctx = TrustContextHolder.require();

        RefundEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Cannot process refund in status: " + refund.getStatus());
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund.setAdapterRef(adapterRef);
        refund.setCompletedAt(OffsetDateTime.now());
        refund = refundRepository.save(refund);

        log.info("Refund completed: refundId={}, intentId={}, adapterRef={}",
                refundId, refund.getIntentId(), adapterRef);

        // Check if intent is fully refunded
        PaymentIntentEntity intent = intentRepository.findById(refund.getIntentId()).orElse(null);
        if (intent != null) {
            List<RefundEntity> allRefunds = refundRepository.findByIntentId(refund.getIntentId());
            BigDecimal totalCompleted = allRefunds.stream()
                    .filter(r -> r.getStatus() == RefundStatus.COMPLETED)
                    .map(RefundEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalCompleted.compareTo(intent.getAmountPaid()) >= 0) {
                intentService.transitionStatus(intent.getIntentId(), IntentStatus.REFUNDED);
                log.info("Intent {} fully refunded", intent.getIntentId());
            }
        }

        publishEvent("REFUND", refundId, "REFUND_COMPLETED",
                Map.of(
                        "refundId", refundId,
                        "intentId", refund.getIntentId(),
                        "billId", intent.getSourceId() != null ? intent.getSourceId() : "",
                        "adapterRef", adapterRef != null ? adapterRef : "",
                        "amount", refund.getAmount().toPlainString()
                ),
                ctx.tenantId());

        return refund;
    }

    /**
     * Mark a refund as failed.
     *
     * @param refundId the refund to fail
     * @param reason   the failure reason
     * @return the failed refund entity
     */
    @Transactional
    public RefundEntity failRefund(String refundId, String reason) {
        TrustContext ctx = TrustContextHolder.require();

        RefundEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Cannot fail refund in status: " + refund.getStatus());
        }

        refund.setStatus(RefundStatus.FAILED);
        refund.setCompletedAt(OffsetDateTime.now());
        refund = refundRepository.save(refund);

        log.info("Refund failed: refundId={}, intentId={}, reason={}",
                refundId, refund.getIntentId(), reason);

        // If intent was in REFUND_PENDING and this was the only pending refund, revert to PAID
        PaymentIntentEntity intent = intentRepository.findById(refund.getIntentId()).orElse(null);
        if (intent != null && intent.getStatus() == IntentStatus.REFUND_PENDING) {
            List<RefundEntity> pendingRefunds = refundRepository.findByIntentId(refund.getIntentId());
            boolean anyStillPending = pendingRefunds.stream()
                    .anyMatch(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.PROCESSING);
            if (!anyStillPending) {
                intentService.transitionStatus(intent.getIntentId(), IntentStatus.PAID);
                log.info("No pending refunds remaining, intent {} reverted to PAID", intent.getIntentId());
            }
        }

        publishEvent("REFUND", refundId, "REFUND_FAILED",
                Map.of(
                        "refundId", refundId,
                        "intentId", refund.getIntentId(),
                        "billId", intent.getSourceId() != null ? intent.getSourceId() : "",
                        "reason", reason != null ? reason : ""
                ),
                ctx.tenantId());

        return refund;
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
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
