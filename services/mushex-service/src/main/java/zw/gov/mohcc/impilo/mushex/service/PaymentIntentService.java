package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.PaymentIntentType;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.integration.CredentialVerificationClient;
import zw.gov.mohcc.impilo.mushex.integration.MusheWalletAdapter;
import zw.gov.mohcc.impilo.mushex.integration.ProviderContractClient;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionPolicy;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionRequest;
import zw.gov.mohcc.impilo.mushex.service.rail.RailSelectionResult;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        VALID_TRANSITIONS.put(IntentStatus.PARTIALLY_PAID, Set.of());
        VALID_TRANSITIONS.put(IntentStatus.SUBMITTED, Set.of());
        VALID_TRANSITIONS.put(IntentStatus.ADJUDICATED, Set.of());
        VALID_TRANSITIONS.put(IntentStatus.REJECTED, Set.of());
    }

    /** Payment method metadata key; when set to this value the wallet adapter is used. */
    public static final String PAYMENT_METHOD_WALLET = "MUSHE_WALLET";

    private final PaymentIntentRepository intentRepository;
    private final EventOutboxRepository outboxRepository;
    private final ReceiptService receiptService;
    private final ObjectMapper objectMapper;
    private final CredentialVerificationClient credentialVerificationClient;
    private final ProviderContractClient providerContractClient;
    private final MusheWalletAdapter musheWalletAdapter;
    private final MushexProperties mushexProperties;
    private final RailSelectionPolicy railSelectionPolicy;

    public PaymentIntentService(PaymentIntentRepository intentRepository,
                                EventOutboxRepository outboxRepository,
                                ReceiptService receiptService,
                                ObjectMapper objectMapper,
                                CredentialVerificationClient credentialVerificationClient,
                                ProviderContractClient providerContractClient,
                                MusheWalletAdapter musheWalletAdapter,
                                MushexProperties mushexProperties,
                                RailSelectionPolicy railSelectionPolicy) {
        this.intentRepository = intentRepository;
        this.outboxRepository = outboxRepository;
        this.receiptService = receiptService;
        this.objectMapper = objectMapper;
        this.credentialVerificationClient = credentialVerificationClient;
        this.providerContractClient = providerContractClient;
        this.musheWalletAdapter = musheWalletAdapter;
        this.mushexProperties = mushexProperties;
        this.railSelectionPolicy = railSelectionPolicy;
    }

    /**
     * Create a new payment intent. If an intent with the same idempotency key already
     * exists, the existing intent is returned without creating a duplicate.
     *
     * <p>This 7-arg legacy method preserves the existing wire shape for callers that have
     * not yet started supplying rail-selection inputs. It delegates to
     * {@link #createIntent(SourceType, String, BigDecimal, String, UUID, String, String, RailSelectionRequest)}
     * with a {@code null} selection request — the policy then applies the deployment-default
     * rail (see {@code docs/design/g4-rail-selection-policy.md}).
     */
    @Transactional
    public PaymentIntentEntity createIntent(SourceType sourceType,
                                            String sourceId,
                                            BigDecimal amount,
                                            String currency,
                                            UUID facilityId,
                                            String idempotencyKey,
                                            String metadata) {
        return createIntent(sourceType, sourceId, amount, currency, facilityId, idempotencyKey, metadata, null);
    }

    /**
     * Create a new payment intent with optional caller-supplied rail-selection inputs.
     * See {@code docs/design/g4-rail-selection-policy.md} for the full algorithm.
     *
     * @param selectionRequest optional. {@code null} means "use the deployment default";
     *                         a non-null instance carries the caller's preferred rail,
     *                         fallback policy and direct-gateway preference. The
     *                         {@code impiloSimulation} flag on the request is overridden
     *                         with the in-metadata {@code impilo_simulation} value if the
     *                         latter is true.
     */
    @Transactional
    public PaymentIntentEntity createIntent(SourceType sourceType,
                                            String sourceId,
                                            BigDecimal amount,
                                            String currency,
                                            UUID facilityId,
                                            String idempotencyKey,
                                            String metadata,
                                            RailSelectionRequest selectionRequest) {
        TrustContext ctx = TrustContextHolder.require();

        UUID facility = facilityId != null ? facilityId : ctx.facilityId();

        Optional<PaymentIntentEntity> existing = intentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency hit: returning existing intent for key={}", idempotencyKey);
            return existing.get();
        }

        boolean isImpiloSim = isImpiloSimulation(metadata);
        boolean simBypass = mushexProperties.getAdapters().getSandbox().isBypassCredentialCheckForSimulation()
                && isImpiloSim;

        CredentialVerificationClient.CredentialVerificationResult cred;
        if (simBypass) {
            cred = new CredentialVerificationClient.CredentialVerificationResult(
                    true, "SIMULATION", "impilo-simulation", null);
            log.info("Impilo sandbox simulation: skipping provider credential verification for new intent");
        } else {
            String providerId = extractProviderId(metadata, facility);
            cred = credentialVerificationClient.verifyPayee(ctx.tenantId(), providerId);
            if (!cred.allowed()) {
                log.warn("Provider credential verification failed before intent create: providerId={} status={}",
                        providerId, cred.status());
                throw new IllegalStateException("PROVIDER_CREDENTIAL_INVALID");
            }
            log.info("Provider credential verification passed before intent create: providerId={} status={} ref={}",
                    providerId, cred.status(), cred.verificationRef());
        }

        if (!simBypass) {
            String payerId = readMetadataField(metadata, "payer_id", "payerId", "insurer_id", "insurerId");
            if (payerId != null && !payerId.isBlank()) {
                if (!providerContractClient.hasActiveContract(ctx.tenantId().toString(), facility.toString(), payerId)) {
                    log.warn("No active contract for facility={} payer={} — blocking payment", facility, payerId);
                    throw new IllegalStateException(
                            "No active provider contract for this facility and payer combination");
                }
            }
        }

        RailSelectionResult selection = runRailSelection(selectionRequest, sourceType, amount, currency, facility, isImpiloSim);
        String mergedMetadata = mergeRailSelectionIntoMetadata(metadata, selection);

        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(UlidGenerator.generate());
        intent.setTenantId(ctx.tenantId());
        intent.setFacilityId(facility);
        intent.setSourceType(sourceType);
        intent.setSourceId(sourceId);
        intent.setAmountTotal(amount);
        intent.setAmountPaid(BigDecimal.ZERO);
        intent.setCurrency(currency);
        intent.setStatus(IntentStatus.CREATED);
        intent.setIdempotencyKey(idempotencyKey);
        intent.setMetadata(mergedMetadata);
        intent.setIntentType(parseIntentTypeFromMetadata(metadata));
        intent.setExpiresAt(OffsetDateTime.now().plusHours(24));
        if (cred.verificationRef() != null && !cred.verificationRef().isBlank()) {
            intent.setCredentialVerificationRef(cred.verificationRef());
        }

        intent = intentRepository.save(intent);

        applySimulationOutcomeOnCreateIfEnabled(intent, metadata);

        log.info("Created payment intent: id={}, source={}/{}, amount={} {}, status={}, rail={}, railReason={}",
                intent.getIntentId(), sourceType, sourceId, amount, currency, intent.getStatus(),
                selection.effectiveRail(), selection.reason());

        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("intentId", intent.getIntentId());
        outboxPayload.put("sourceType", sourceType.name());
        outboxPayload.put("sourceId", sourceId);
        outboxPayload.put("amount", amount.toPlainString());
        outboxPayload.put("currency", currency);
        outboxPayload.put("facilityId", intent.getFacilityId().toString());
        outboxPayload.put("status", intent.getStatus().name());
        outboxPayload.put("effectiveRail", selection.effectiveRail().name());
        outboxPayload.put("preferredRail",
                selection.preferredRail() != null ? selection.preferredRail().name() : "NONE");
        outboxPayload.put("railSelectionReason", selection.reason().name());

        publishEvent("PAYMENT_INTENT", intent.getIntentId(), "INTENT_CREATED", outboxPayload, ctx.tenantId());

        return intent;
    }

    /**
     * Run the rail-selection policy. The caller's {@code selectionRequest} is honoured
     * as supplied, except that the {@code impiloSimulation} flag is forced to {@code true}
     * whenever the request metadata also signals an Impilo simulation; this keeps the
     * safety guarantee that simulation runs never auto-route to a non-SANDBOX rail.
     */
    private RailSelectionResult runRailSelection(RailSelectionRequest request,
                                                 SourceType sourceType,
                                                 BigDecimal amount,
                                                 String currency,
                                                 UUID facility,
                                                 boolean isImpiloSim) {
        RailSelectionRequest effective;
        if (request == null) {
            effective = RailSelectionRequest.empty(sourceType, amount, currency, facility, isImpiloSim);
        } else if (isImpiloSim && !request.impiloSimulation()) {
            effective = new RailSelectionRequest(
                    request.preferredRailAdapter(),
                    request.allowFallback(),
                    request.directGatewayAllowed(),
                    request.sourceType() != null ? request.sourceType() : sourceType,
                    request.amount() != null ? request.amount() : amount,
                    request.currency() != null ? request.currency() : currency,
                    request.facilityId() != null ? request.facilityId() : facility,
                    true);
        } else {
            effective = request;
        }
        return railSelectionPolicy.select(effective);
    }

    /**
     * Merge the rail-selection result into the caller-supplied metadata JSON under the
     * reserved {@code rail_selection} key. Defensive against:
     * <ul>
     *   <li>{@code null}/blank metadata — produces a fresh object with only {@code rail_selection}.</li>
     *   <li>Non-object metadata (string/array/number) — preserves the original; rail-selection
     *       is then visible only on the outbox event.</li>
     *   <li>Mocked {@link ObjectMapper} returning {@code null} for {@code readTree} / {@code createObjectNode}
     *       — preserves the original metadata so this method never throws.</li>
     * </ul>
     */
    private String mergeRailSelectionIntoMetadata(String inboundMetadata, RailSelectionResult result) {
        try {
            JsonNode parsed = null;
            if (inboundMetadata != null && !inboundMetadata.isBlank()) {
                parsed = objectMapper.readTree(inboundMetadata);
            }
            ObjectNode root;
            if (parsed == null) {
                root = objectMapper.createObjectNode();
            } else if (parsed instanceof ObjectNode obj) {
                root = obj;
            } else {
                log.warn("Inbound payment-intent metadata is not a JSON object; rail_selection recorded only on outbox");
                return inboundMetadata;
            }
            if (root == null) {
                return inboundMetadata;
            }
            ObjectNode rs = objectMapper.createObjectNode();
            rs.put("effective_rail", result.effectiveRail().name());
            if (result.preferredRail() != null) {
                rs.put("preferred_rail", result.preferredRail().name());
            } else {
                rs.putNull("preferred_rail");
            }
            rs.put("fallback_applied", result.fallbackApplied());
            rs.put("direct_gateway_requested", result.directGatewayRequested());
            rs.put("reason", result.reason().name());
            if (result.reasonDetail() != null) {
                rs.put("reason_detail", result.reasonDetail());
            }
            rs.put("selected_at", OffsetDateTime.now().toString());
            rs.put("selection_version", "1");
            root.set("rail_selection", rs);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to merge rail_selection into intent metadata; preserving original: {}", e.getMessage());
            return inboundMetadata;
        }
    }

    private PaymentIntentType parseIntentTypeFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(metadata);
            if (n.hasNonNull("intent_type")) {
                return PaymentIntentType.valueOf(n.get("intent_type").asText().trim());
            }
            if (n.hasNonNull("payment_intent_type")) {
                return PaymentIntentType.valueOf(n.get("payment_intent_type").asText().trim());
            }
        } catch (Exception e) {
            log.debug("Could not parse intent_type from metadata: {}", e.getMessage());
        }
        return null;
    }

    private void applySimulationOutcomeOnCreateIfEnabled(PaymentIntentEntity intent, String metadata) {
        if (!mushexProperties.getAdapters().getSandbox().isApplySimulationOutcomeOnCreate()) {
            return;
        }
        if (!isImpiloSimulation(metadata)) {
            return;
        }
        String outcomeRaw = readMetadataField(metadata, "simulation_outcome", "simulationOutcome");
        IntentStatus mapped = mapSimulationOutcome(outcomeRaw);
        if (mapped == null || mapped == IntentStatus.CREATED) {
            return;
        }
        intent.setStatus(mapped);
        if (mapped == IntentStatus.PAID) {
            intent.setAmountPaid(intent.getAmountTotal());
        } else if (mapped == IntentStatus.PARTIALLY_PAID) {
            intent.setAmountPaid(intent.getAmountTotal()
                    .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP)
                    .min(intent.getAmountTotal()));
        }
        intentRepository.save(intent);
    }

    private static IntentStatus mapSimulationOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "pending" -> IntentStatus.PENDING;
            case "authorised", "authorized" -> IntentStatus.AUTHORIZED;
            case "paid" -> IntentStatus.PAID;
            case "failed" -> IntentStatus.FAILED;
            case "reversed" -> IntentStatus.CANCELLED;
            case "partially_paid" -> IntentStatus.PARTIALLY_PAID;
            case "submitted" -> IntentStatus.SUBMITTED;
            case "adjudicated" -> IntentStatus.ADJUDICATED;
            case "rejected" -> IntentStatus.REJECTED;
            default -> null;
        };
    }

    private boolean isImpiloSimulation(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            if (n.has("impilo_simulation") && n.get("impilo_simulation").asBoolean()) {
                return true;
            }
            return n.has("impiloSimulation") && n.get("impiloSimulation").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fetch a payment intent by ID, throwing if not found.
     * Throws {@link IntentNotFoundException} (a subtype of {@link IllegalArgumentException}
     * for backward compatibility) so the controller can map to HTTP 404 cleanly.
     */
    public PaymentIntentEntity getIntent(String intentId) {
        return intentRepository.findById(intentId)
                .orElseThrow(() -> new IntentNotFoundException(intentId));
    }

    /**
     * Find all payment intents for a given source.
     */
    public List<PaymentIntentEntity> findBySource(SourceType sourceType, String sourceId) {
        return intentRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
    }

    public List<PaymentIntentEntity> findByTenantAndSource(UUID tenantId, SourceType sourceType, String sourceId) {
        return intentRepository.findByTenantIdAndSourceTypeAndSourceIdOrderByCreatedAtAsc(
                tenantId, sourceType, sourceId);
    }

    public List<PaymentIntentEntity> findByTenantAndSourceIds(UUID tenantId, SourceType sourceType, Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return intentRepository.findByTenantIdAndSourceTypeAndSourceIdInOrderByCreatedAtAsc(
                tenantId, sourceType, sourceIds);
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
            String providerId = extractProviderId(intent.getMetadata(), intent.getFacilityId());
            CredentialVerificationClient.CredentialVerificationResult cred =
                    credentialVerificationClient.verifyPayee(ctx.tenantId(), providerId);
            if (!cred.allowed()) {
                log.warn("Provider credential verification failed before PAID: intentId={} providerId={} status={}",
                        intentId, providerId, cred.status());
                throw new IllegalStateException("PROVIDER_CREDENTIAL_INVALID");
            }
            log.info("Provider credential verification passed before PAID: intentId={} providerId={} status={} ref={}",
                    intentId, providerId, cred.status(), cred.verificationRef());
            if (cred.verificationRef() != null && !cred.verificationRef().isBlank()) {
                intent.setCredentialVerificationRef(cred.verificationRef());
            }
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
     * Cancels CREATED/PENDING intents when a credential is revoked (Kafka path — no {@link TrustContext}).
     */
    @Transactional
    public void cancelPendingIntentsForCredentialRevocation(UUID tenantId,
                                                            String subjectType,
                                                            String subjectId,
                                                            String reason) {
        List<PaymentIntentEntity> candidates = intentRepository.findByTenantIdAndStatusIn(
                tenantId, List.of(IntentStatus.CREATED, IntentStatus.PENDING));
        for (PaymentIntentEntity intent : candidates) {
            if (matchesCredentialSubject(intent, subjectType, subjectId)) {
                systemCancelIntent(intent, reason);
            }
        }
    }

    private void systemCancelIntent(PaymentIntentEntity intent, String reason) {
        if (intent.getStatus() != IntentStatus.CREATED && intent.getStatus() != IntentStatus.PENDING) {
            return;
        }
        IntentStatus oldStatus = intent.getStatus();
        intent.setStatus(IntentStatus.CANCELLED);
        intentRepository.save(intent);
        log.info("Intent {} cancelled by system ({}): {} -> CANCELLED",
                intent.getIntentId(), reason, oldStatus);
        publishEvent("PAYMENT_INTENT", intent.getIntentId(), "STATUS_CHANGED",
                Map.of(
                        "intentId", intent.getIntentId(),
                        "fromStatus", oldStatus.name(),
                        "toStatus", IntentStatus.CANCELLED.name(),
                        "reason", reason
                ),
                intent.getTenantId());
    }

    private boolean matchesCredentialSubject(PaymentIntentEntity intent,
                                             String subjectType,
                                             String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return false;
        }
        String st = subjectType != null ? subjectType.toUpperCase(Locale.ROOT) : "";
        if (st.contains("FACILITY")) {
            try {
                UUID sid = UUID.fromString(subjectId.trim());
                return sid.equals(intent.getFacilityId());
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        String metaPid = readMetadataField(intent.getMetadata(), "provider_id", "providerId");
        return subjectId.equals(metaPid);
    }

    private String readMetadataField(String metadataJson, String... keys) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            for (String k : keys) {
                if (n.hasNonNull(k)) {
                    String v = n.get(k).asText();
                    if (v != null && !v.isBlank()) {
                        return v;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse intent metadata: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Execute payment for an intent using the Mushe Wallet.
     *
     * <p>This is the wallet-specific payment path. It reads the patient CPID from
     * the intent metadata and debits their wallet for the full outstanding amount.
     * If the intent metadata contains a {@code payment_method} of {@code MUSHE_WALLET},
     * callers should use this method instead of external payment processors.</p>
     *
     * @return the updated intent (will be PAID if the wallet debit succeeds)
     */
    @Transactional
    public PaymentIntentEntity executeWalletPayment(String intentId) {
        TrustContext ctx = TrustContextHolder.require();
        PaymentIntentEntity intent = getIntent(intentId);

        if (intent.getStatus() != IntentStatus.CREATED
                && intent.getStatus() != IntentStatus.PENDING
                && intent.getStatus() != IntentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot execute wallet payment for intent in status: " + intent.getStatus());
        }

        BigDecimal outstanding = intent.getAmountTotal().subtract(intent.getAmountPaid());
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Intent already fully paid: " + intentId);
        }

        String patientCpid = readMetadataField(intent.getMetadata(),
                "patient_cpid", "patientCpid", "payer_cpid");
        if (patientCpid == null || patientCpid.isBlank()) {
            throw new IllegalStateException(
                    "Wallet payment requires patient_cpid in intent metadata: " + intentId);
        }

        MusheWalletAdapter.WalletPaymentResult result = musheWalletAdapter.payFromWallet(
                ctx.tenantId(),
                patientCpid,
                outstanding,
                intent.getIntentId(),
                ctx.correlationId() != null ? ctx.correlationId().toString() : null);

        log.info("Wallet payment executed: intentId={} txnId={} status={} amount={}",
                intentId, result.transactionId(), result.status(), result.amount());

        return recordPayment(intentId, outstanding);
    }

    /**
     * Check whether an intent's metadata indicates MUSHE_WALLET as the payment method.
     */
    public boolean isWalletPayment(PaymentIntentEntity intent) {
        String method = readMetadataField(intent.getMetadata(),
                "payment_method", "paymentMethod");
        return PAYMENT_METHOD_WALLET.equalsIgnoreCase(method);
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
    private String extractProviderId(String metadata, UUID facilityId) {
        if (metadata != null && !metadata.isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(metadata);
                if (n.hasNonNull("provider_id")) {
                    String v = n.get("provider_id").asText();
                    if (!v.isBlank()) {
                        return v;
                    }
                }
                if (n.hasNonNull("providerId")) {
                    String v = n.get("providerId").asText();
                    if (!v.isBlank()) {
                        return v;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse payment intent metadata for provider_id: {}", e.getMessage());
            }
        }
        if (facilityId != null) {
            return facilityId.toString();
        }
        throw new IllegalStateException("Payee provider_id is required (set provider_id in metadata or pass facilityId)");
    }

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
