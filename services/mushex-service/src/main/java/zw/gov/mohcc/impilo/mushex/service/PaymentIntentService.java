package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Core payment intent state machine.
 *
 * A PaymentIntent represents a single billable obligation. It progresses through
 * states (CREATED -> PENDING -> AUTHORIZED -> PAID) with guard rails preventing
 * invalid transitions. Idempotency keys ensure duplicate requests are safely
 * deduplicated.
 */
@Service
public class PaymentIntentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntentService.class);

    /**
     * Valid state transitions: from-status -> set of allowed to-statuses.
     */
    private static final Map<IntentStatus, Set<IntentStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(IntentStatus.class);
        VALID_TRANSITIONS.put(IntentStatus.CREATED, Set.of(
                IntentStatus.PENDING, IntentStatus.CANCELLED));
        VALID_TRANSITIONS.put(IntentStatus.PENDING, Set.of(
                IntentStatus.AUTHORIZED, IntentStatus.PAID, IntentStatus.FAILED, IntentStatus.CANCELLED));
        VALID_TRANSITIONS.put(IntentStatus.AUTHORIZED, Set.of(
                IntentStatus.PAID, IntentStatus.FAILED, IntentStatus.CANCELLED));
        VALID_TRANSITIONS.put(IntentStatus.PAID, Set.of(
                IntentStatus.REFUND_PENDING));
        VALID_TRANSITIONS.put(IntentStatus.REFUND_PENDING, Set.of(
                IntentStatus.REFUNDED, IntentStatus.PAID));
        VALID_TRANSITIONS.put(IntentStatus.FAILED, Set.of());
        VALID_TRANSITIONS.put(IntentStatus.CANCELLED, Set.of());
        VALID_TRANSITIONS.put(IntentStatus.REFUNDED, Set.of());
    }

    private final PaymentIntentRepository intentRepository;
    private final EventOutboxRepository outboxRepository;
    private final ReceiptService receiptService;
    private final ObjectMapper objectMapper;

    public PaymentIntentService(PaymentIntentRepository intentRepository,
                                EventOutboxRepository outboxRepository,
                                ReceiptService receiptService,
                                ObjectMapper objectMapper) {
        this.intentRepository = intentRepository;
        this.outboxRepository = outboxRepository;
        this.receiptService = receiptService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new payment intent. If an intent with the same idempotency key already
     * exists, the existing intent is returned without creating a duplicate.
     */
    @Transactional
    public PaymentIntentEntity createIntent(SourceType sourceType,
                                            String sourceId,
                                            BigDecimal amount,
                                            String currency,
                                            UUID facilityId,
                                            String idempotencyKey,
                                            String metadata) {
        TrustContext ctx = TrustContextHolder.require();

        // Idempotency check: return existing intent if key already used
        Optional<PaymentIntentEntity> existing = intentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency hit: returning existing intent for key={}", idempotencyKey);
            return existing.get();
        }

        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(ctx.tenantId());
        intent.setFacilityId(facilityId != null ? facilityId : ctx.facilityId());
        intent.setSourceType(sourceType);
        intent.setSourceId(sourceId);
        intent.setAmountTotal(amount);
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setCurrency(currency);
        intent.setStatus(IntentStatus.CREATED);
        intent.setIdempotencyKey(idempotencyKey);
        intent.setMetadata(metadata);
        intent.setExpiresAt(OffsetDateTime.now().plusHours(24));

        intent = intentRepository.save(intent);

        log.info("Created payment intent: id={}, source={}/{}, amount={} {}",
                intent.getIntentId(), sourceType, sourceId, amount, currency);

        publishEvent("PAYMENT_INTENT", intent.getIntentId(), "INTENT_CREATED",
                Map.of(
                        "intentId", intent.getIntentId(),
                        "sourceType", sourceType.name(),
                        "sourceId", sourceId,
                        "amount", amount.toPlainString(),
                        "currency", currency,
                        "facilityId", intent.getFacilityId().toString()
                ),
                ctx.tenantId());

        return intent;
    }

    /**
     * Fetch a payment intent by ID, throwing if not found.
     */
    public PaymentIntentEntity getIntent(String intentId) {
        return intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));
    }

    /**
     * Find all payment intents for a given source.
     */
    public List<PaymentIntentEntity> findBySource(SourceType sourceType, String sourceId) {
        return intentRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
    }

    public String findIntentIdBySource(SourceType sourceType, String sourceId) {
        return intentRepository.findBySourceTypeAndSourceId(sourceType, sourceId).stream()
                .findFirst()
                .map(PaymentIntentEntity::getIntentId)
                .orElse(null);
    }

    /**
     * Transition the intent to a new status with state machine validation.
     */
    @Transactional
    public PaymentIntentEntity transitionStatus(String intentId, IntentStatus newStatus) {
        TrustContext ctx = TrustContextHolder.require();
        PaymentIntentEntity intent = getIntent(intentId);

        validateTransition(intent.getStatus(), newStatus);

        IntentStatus oldStatus = intent.getStatus();
        intent.setStatus(newStatus);
        intent = intentRepository.save(intent);

        log.info("Intent {} transitioned: {} -> {}", intentId, oldStatus, newStatus);

        publishEvent("PAYMENT_INTENT", intentId, "STATUS_CHANGED",
                Map.of(
                        "intentId", intentId,
                        "fromStatus", oldStatus.name(),
                        "toStatus", newStatus.name()
                ),
                ctx.tenantId());

        return intent;
    }

    /**
     * Record an incoming payment against the intent.
     * If amountPaid >= amountTotal after recording, the intent transitions to PAID
     * and a receipt is generated.
     */
    @Transactional
    public PaymentIntentEntity recordPayment(String intentId, BigDecimal amount) {
        TrustContext ctx = TrustContextHolder.require();
        PaymentIntentEntity intent = getIntent(intentId);

        if (intent.getStatus() != IntentStatus.PENDING
                && intent.getStatus() != IntentStatus.AUTHORIZED
                && intent.getStatus() != IntentStatus.CREATED) {
            throw new IllegalStateException(
                    "Cannot record payment for intent in status: " + intent.getStatus());
        }

        BigDecimal newAmountPaid = intent.getAmountPaid().add(amount);
        intent.setAmountPaid(newAmountPaid);

        log.info("Recorded payment of {} for intent {}: total paid now {}",
                amount, intentId, newAmountPaid);

        if (newAmountPaid.compareTo(intent.getAmountTotal()) >= 0) {
            IntentStatus oldStatus = intent.getStatus();
            intent.setStatus(IntentStatus.PAID);
            intent = intentRepository.save(intent);

            log.info("Intent {} fully paid: {} -> PAID", intentId, oldStatus);

            // Generate receipt
            receiptService.generateReceipt(intentId);

            publishEvent("PAYMENT_INTENT", intentId, "STATUS_CHANGED",
                    Map.of(
                            "intentId", intentId,
                            "fromStatus", oldStatus.name(),
                            "toStatus", IntentStatus.PAID.name(),
                            "amountPaid", newAmountPaid.toPlainString()
                    ),
                    ctx.tenantId());
        } else {
            intent = intentRepository.save(intent);
        }

        return intent;
    }

    /**
     * Cancel an intent. Only allowed from CREATED or PENDING status.
     */
    @Transactional
    public PaymentIntentEntity cancelIntent(String intentId) {
        TrustContext ctx = TrustContextHolder.require();
        PaymentIntentEntity intent = getIntent(intentId);

        if (intent.getStatus() != IntentStatus.CREATED && intent.getStatus() != IntentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot cancel intent in status: " + intent.getStatus());
        }

        IntentStatus oldStatus = intent.getStatus();
        intent.setStatus(IntentStatus.CANCELLED);
        intent = intentRepository.save(intent);

        log.info("Intent {} cancelled from status {}", intentId, oldStatus);

        publishEvent("PAYMENT_INTENT", intentId, "STATUS_CHANGED",
                Map.of(
                        "intentId", intentId,
                        "fromStatus", oldStatus.name(),
                        "toStatus", IntentStatus.CANCELLED.name()
                ),
                ctx.tenantId());

        return intent;
    }

    /**
     * Validate that a state transition is allowed by the state machine.
     */
    private void validateTransition(IntentStatus from, IntentStatus to) {
        Set<IntentStatus> allowed = VALID_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException(
                    String.format("Invalid intent status transition: %s -> %s", from, to));
        }
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
