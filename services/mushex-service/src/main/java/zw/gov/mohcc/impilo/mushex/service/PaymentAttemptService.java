package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentAttemptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.AttemptStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailEnforcementReason;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentAttemptRepository;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcement;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementException;
import zw.gov.mohcc.impilo.mushex.service.rail.RailEnforcementOutcome;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 3 — attempt-time rail enforcement.
 *
 * <p>This service owns the payment-attempt creation flow that was missing in the
 * audit (see {@code docs/design/phase-3-attempt-time-rail-enforcement.md}). It:
 * <ul>
 *   <li>Looks up the intent and validates it is in a payable status.</li>
 *   <li>Short-circuits on idempotency replay.</li>
 *   <li>Delegates to {@link RailEnforcement} to determine the effective rail.</li>
 *   <li>Applies the <strong>safety gate</strong>: real-money rails are <em>not</em>
 *       called unless both {@code mushex.adapters.&lt;rail&gt;.enabled} and
 *       {@code credentials-configured} are true. SANDBOX is always safe.</li>
 *   <li>Calls the adapter (when allowed), maps the response, persists the attempt
 *       with its full enforcement-audit trail.</li>
 *   <li>Records an {@code ATTEMPT_INITIATED} or
 *       {@code ATTEMPT_FAILED_PRE_INITIATION} outbox event.</li>
 * </ul>
 *
 * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md}.
 */
@Service
public class PaymentAttemptService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAttemptService.class);

    /** Intent statuses from which an attempt may be initiated. */
    private static final Set<IntentStatus> PAYABLE_STATUSES = EnumSet.of(
            IntentStatus.CREATED, IntentStatus.PENDING, IntentStatus.AUTHORIZED);

    private final PaymentIntentService paymentIntentService;
    private final PaymentAttemptRepository attemptRepository;
    private final EventOutboxRepository outboxRepository;
    private final AdapterRegistry adapterRegistry;
    private final RailEnforcement railEnforcement;
    private final MushexProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentAttemptService(PaymentIntentService paymentIntentService,
                                 PaymentAttemptRepository attemptRepository,
                                 EventOutboxRepository outboxRepository,
                                 AdapterRegistry adapterRegistry,
                                 RailEnforcement railEnforcement,
                                 MushexProperties properties,
                                 ObjectMapper objectMapper) {
        this.paymentIntentService = paymentIntentService;
        this.attemptRepository = attemptRepository;
        this.outboxRepository = outboxRepository;
        this.adapterRegistry = adapterRegistry;
        this.railEnforcement = railEnforcement;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiate a payment attempt for the given intent.
     *
     * @param intentId       the persisted intent identifier.
     * @param idempotencyKey optional caller-supplied idempotency key. When supplied and a
     *                       prior attempt exists for {@code (intentId, idempotencyKey)},
     *                       the existing attempt is returned with no side effects.
     * @return the attempt entity (newly created or replayed).
     * @throws AttemptNotPayableException when the intent is in a non-payable status.
     * @throws RailEnforcementException   when the rail resolved from the intent's metadata
     *                                    cannot be honoured at attempt time.
     */
    @Transactional
    public PaymentAttemptEntity initiateAttempt(String intentId, String idempotencyKey) {
        TrustContext ctx = TrustContextHolder.require();

        PaymentIntentEntity intent = paymentIntentService.getIntent(intentId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentAttemptEntity> existing =
                    attemptRepository.findByIntentIdAndIdempotencyKey(intentId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency hit: returning existing attempt {} for intent {} and key {}",
                        existing.get().getId(), intentId, idempotencyKey);
                return existing.get();
            }
        }

        if (!PAYABLE_STATUSES.contains(intent.getStatus())) {
            log.warn("Intent {} is in status {} which is not payable", intentId, intent.getStatus());
            publishFailedPreInitiation(intent, idempotencyKey,
                    "INTENT_NOT_PAYABLE",
                    "Intent " + intentId + " is in non-payable status " + intent.getStatus(),
                    null, ctx.tenantId());
            throw new AttemptNotPayableException(
                    "Intent " + intentId + " is in non-payable status " + intent.getStatus());
        }

        RailEnforcementOutcome outcome;
        try {
            outcome = railEnforcement.resolve(intent, properties.getRailSelection(), adapterRegistry);
        } catch (RailEnforcementException ex) {
            publishFailedPreInitiation(intent, idempotencyKey,
                    ex.getErrorCode(), ex.getMessage(), null, ctx.tenantId());
            throw ex;
        }

        AdapterType effective = outcome.effectiveRail();
        SafetyGate gate = evaluateSafetyGate(effective);

        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.setId(UlidGenerator.generate());
        attempt.setIntentId(intent.getIntentId());
        attempt.setAdapterType(effective);
        attempt.setAmount(intent.getAmountTotal());
        attempt.setIdempotencyKey(idempotencyKey != null && idempotencyKey.isBlank() ? null : idempotencyKey);

        AdapterResponse adapterResponse = null;
        AttemptStatus mappedStatus;
        if (gate.isAllowed()) {
            PaymentRailAdapter adapter = adapterRegistry.getAdapter(effective);
            try {
                adapterResponse = adapter.initiatePayment(
                        intent.getIntentId(),
                        intent.getAmountTotal(),
                        intent.getCurrency(),
                        Map.of());
            } catch (Exception ex) {
                log.error("Rail adapter {} threw initiating attempt for intent {}: {}",
                        effective, intentId, ex.getMessage(), ex);
                publishFailedPreInitiation(intent, idempotencyKey,
                        "ADAPTER_INITIATION_FAILED",
                        "Adapter " + effective + " threw during initiatePayment: " + ex.getMessage(),
                        outcome, ctx.tenantId());
                throw new IllegalStateException(
                        "Adapter " + effective + " failed to initiate payment", ex);
            }
            mappedStatus = mapAdapterStatus(adapterResponse != null ? adapterResponse.status() : null);
            attempt.setAdapterRef(adapterResponse != null ? adapterResponse.adapterRef() : null);
            attempt.setRawSummary(stringifyAdapterResponse(adapterResponse));
        } else {
            log.info("Safety gate BLOCKED_PRE_LIVE for rail {} on intent {}; persisting attempt without adapter call",
                    effective, intentId);
            mappedStatus = AttemptStatus.INITIATED;
            attempt.setAdapterRef(null);
            attempt.setRawSummary(null);
        }

        attempt.setStatus(mappedStatus);
        attempt.setEnforcementMetadata(buildEnforcementMetadataJson(outcome, gate));
        if (mappedStatus == AttemptStatus.SUCCEEDED || mappedStatus == AttemptStatus.FAILED) {
            attempt.setCompletedAt(OffsetDateTime.now());
        }

        try {
            attempt = attemptRepository.save(attempt);
        } catch (DataIntegrityViolationException dive) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<PaymentAttemptEntity> winner =
                        attemptRepository.findByIntentIdAndIdempotencyKey(intentId, idempotencyKey);
                if (winner.isPresent()) {
                    log.info("Concurrent attempt with same idempotency key resolved; returning winner {}",
                            winner.get().getId());
                    return winner.get();
                }
            }
            throw dive;
        }

        if (mappedStatus == AttemptStatus.SUCCEEDED) {
            paymentIntentService.recordPayment(intent.getIntentId(), intent.getAmountTotal());
        }

        publishAttemptInitiated(attempt, outcome, gate, intent, ctx.tenantId());

        log.info("Attempt {} initiated for intent {}: rail={}, reason={}, gate={}, status={}",
                attempt.getId(), intent.getIntentId(), effective, outcome.reason(),
                gate.label(), mappedStatus);

        return attempt;
    }

    private SafetyGate evaluateSafetyGate(AdapterType rail) {
        if (rail == AdapterType.SANDBOX) {
            return SafetyGate.allow();
        }
        MushexProperties.RealRail cfg = realRailConfig(rail);
        if (cfg == null) {
            return SafetyGate.block(
                    "no_real_rail_config",
                    "No real-rail config exists for " + rail);
        }
        if (!cfg.isEnabled() || !cfg.isCredentialsConfigured()) {
            return SafetyGate.block(
                    "BLOCKED_PRE_LIVE",
                    "mushex.adapters." + propertyKey(rail) + ".{enabled,credentials-configured} "
                            + "must both be true");
        }
        // Third leg of the safety gate: even with the operator config flags on, refuse to
        // call an adapter that has not declared itself liveCapable. Today every real-rail
        // adapter is a stub returning PENDING; PaymentRailAdapter.liveCapable() defaults to
        // false, so this branch is the runtime guard that prevents a configuration mistake
        // from triggering a real provider call before a real provider client is wired in.
        PaymentRailAdapter adapter = adapterRegistry.getAdapter(rail);
        if (!adapter.liveCapable()) {
            return SafetyGate.block(
                    "BLOCKED_NOT_LIVE_CAPABLE",
                    "Adapter " + rail + " has not declared itself live-capable "
                            + "(PaymentRailAdapter.liveCapable() returned false). "
                            + "This is the runtime guard against operators enabling a "
                            + "real-money rail before a real provider client is wired in.");
        }
        return SafetyGate.allow();
    }

    private MushexProperties.RealRail realRailConfig(AdapterType rail) {
        return switch (rail) {
            case MOBILE_MONEY -> properties.getAdapters().getMobileMoney();
            case BANK_TRANSFER -> properties.getAdapters().getBankTransfer();
            case CARD_GATEWAY -> properties.getAdapters().getCardGateway();
            case WALLET, SANDBOX -> null;
        };
    }

    private String propertyKey(AdapterType rail) {
        return switch (rail) {
            case MOBILE_MONEY -> "mobile-money";
            case BANK_TRANSFER -> "bank-transfer";
            case CARD_GATEWAY -> "card-gateway";
            case WALLET -> "wallet";
            case SANDBOX -> "sandbox";
        };
    }

    private AttemptStatus mapAdapterStatus(String status) {
        if (status == null) {
            return AttemptStatus.PROCESSING;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED" -> AttemptStatus.SUCCEEDED;
            case "FAILED", "ERROR", "DECLINED" -> AttemptStatus.FAILED;
            case "CANCELLED", "CANCELED" -> AttemptStatus.CANCELLED;
            case "PENDING", "PROCESSING" -> AttemptStatus.PROCESSING;
            default -> AttemptStatus.PROCESSING;
        };
    }

    private String stringifyAdapterResponse(AdapterResponse response) {
        if (response == null) {
            return null;
        }
        try {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("adapter_ref", response.adapterRef());
            raw.put("status", response.status());
            raw.put("message", response.message());
            raw.put("metadata", response.metadata());
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            log.warn("Failed to serialise adapter response: {}", e.getMessage());
            return null;
        }
    }

    private String buildEnforcementMetadataJson(RailEnforcementOutcome outcome, SafetyGate gate) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("effective_rail", outcome.effectiveRail().name());
            if (outcome.preferredRail() != null) {
                n.put("preferred_rail", outcome.preferredRail().name());
            } else {
                n.putNull("preferred_rail");
            }
            n.put("reason", outcome.reason().name());
            n.put("legacy_intent", outcome.legacyIntent());
            n.put("safety_gate", gate.label());
            if (gate.detail() != null) {
                n.put("safety_gate_detail", gate.detail());
            }
            if (outcome.selectionVersion() != null) {
                n.put("selection_version", outcome.selectionVersion());
            }
            n.put("enforcement_version", "1");
            n.put("resolved_at", OffsetDateTime.now().toString());
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            log.warn("Failed to serialise enforcement metadata: {}", e.getMessage());
            return null;
        }
    }

    private void publishAttemptInitiated(PaymentAttemptEntity attempt,
                                         RailEnforcementOutcome outcome,
                                         SafetyGate gate,
                                         PaymentIntentEntity intent,
                                         UUID tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentId", attempt.getIntentId());
        payload.put("attemptId", attempt.getId());
        payload.put("adapterType", attempt.getAdapterType().name());
        payload.put("preferredRail",
                outcome.preferredRail() != null ? outcome.preferredRail().name() : "NONE");
        payload.put("effectiveRail", outcome.effectiveRail().name());
        payload.put("enforcementReason", outcome.reason().name());
        payload.put("safetyGate", gate.label());
        payload.put("amount", intent.getAmountTotal().toPlainString());
        payload.put("currency", intent.getCurrency());
        payload.put("status", attempt.getStatus().name());
        payload.put("legacyIntent", outcome.legacyIntent());
        if (attempt.getIdempotencyKey() != null) {
            payload.put("idempotencyKey", attempt.getIdempotencyKey());
        }
        if (attempt.getAdapterRef() != null) {
            payload.put("adapterRef", attempt.getAdapterRef());
        }
        publishEvent("PAYMENT_ATTEMPT", attempt.getId(), "ATTEMPT_INITIATED", payload, tenantId);
    }

    private void publishFailedPreInitiation(PaymentIntentEntity intent,
                                            String idempotencyKey,
                                            String errorCode,
                                            String errorMessage,
                                            RailEnforcementOutcome outcome,
                                            UUID tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentId", intent.getIntentId());
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            payload.put("idempotencyKey", idempotencyKey);
        }
        if (outcome != null) {
            payload.put("effectiveRail", outcome.effectiveRail().name());
            payload.put("enforcementReason", outcome.reason().name());
            payload.put("legacyIntent", outcome.legacyIntent());
        }
        publishEvent("PAYMENT_ATTEMPT", intent.getIntentId(),
                "ATTEMPT_FAILED_PRE_INITIATION", payload, tenantId);
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

    /**
     * Result of the safety-gate evaluation. {@link #ALLOWED} is the unique successful
     * value; any other instance carries a {@link RailEnforcementReason}-style label
     * ({@code "BLOCKED_PRE_LIVE"} today) and a human-readable detail.
     */
    static final class SafetyGate {
        private static final SafetyGate ALLOWED = new SafetyGate(true, "OK", null);

        private final boolean allowed;
        private final String label;
        private final String detail;

        private SafetyGate(boolean allowed, String label, String detail) {
            this.allowed = allowed;
            this.label = label;
            this.detail = detail;
        }

        static SafetyGate allow() { return ALLOWED; }

        static SafetyGate block(String label, String detail) {
            return new SafetyGate(false, label, detail);
        }

        boolean isAllowed() { return allowed; }

        String label() { return label; }

        String detail() { return detail; }
    }

    /** Thrown when an attempt is requested against an intent in a non-payable status. */
    public static class AttemptNotPayableException extends IllegalStateException {
        public AttemptNotPayableException(String message) {
            super(message);
        }
    }
}
