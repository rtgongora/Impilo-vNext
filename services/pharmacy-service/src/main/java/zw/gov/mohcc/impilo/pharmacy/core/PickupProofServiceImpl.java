package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyProperties;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupVerificationGrade;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.PickupProofRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages OTP/QR pickup proof creation, claim, and expiration workflows.
 *
 * <p>When a dispense order is ready for pickup, a proof record is created
 * with either an OTP code (6-digit numeric) or a QR token (UUID-based).
 * The plaintext token is returned exactly once to the caller; only the
 * HMAC hash is stored for later verification.</p>
 *
 * <p>OF-B16 additions: every claim records the collector identity and a
 * server-assigned verification grade (§12.4 proof ladder); the named-recipient
 * / SMS-reference path resolves an opaque reference server-side (no token in
 * SMS, §8.11.3); the expiry sweep escalates an uncollected READY episode to
 * CANCELLED with a stock-return reversal and a coded outbox event.</p>
 */
@Service
public class PickupProofServiceImpl implements PickupProofService {

    private static final Logger log = LoggerFactory.getLogger(PickupProofServiceImpl.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Unambiguous alphabet for the opaque SMS reference (no 0/O, 1/I/L). */
    private static final char[] SMS_REFERENCE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SMS_REFERENCE_LENGTH = 8;

    private final PickupProofRepository proofRepository;
    private final EventOutboxRepository outboxRepository;
    private final HmacService hmacService;
    private final PharmacyProperties properties;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;
    private final DispenseOrderRepository orderRepository;
    private final DispenseEngine dispenseEngine;
    private final StockLedgerService stockLedgerService;

    public PickupProofServiceImpl(PickupProofRepository proofRepository,
                                  EventOutboxRepository outboxRepository,
                                  HmacService hmacService,
                                  PharmacyProperties properties,
                                  ObjectMapper objectMapper,
                                  zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification,
                                  DispenseOrderRepository orderRepository,
                                  DispenseEngine dispenseEngine,
                                  StockLedgerService stockLedgerService) {
        this.proofRepository = proofRepository;
        this.outboxRepository = outboxRepository;
        this.hmacService = hmacService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.biometricVerification = biometricVerification;
        this.orderRepository = orderRepository;
        this.dispenseEngine = dispenseEngine;
        this.stockLedgerService = stockLedgerService;
    }

    @Override
    @Transactional
    public PickupProofEntity createProof(UUID orderId, PickupMethod method, String delegatedTo) {
        return createProof(orderId, method, delegatedTo, null);
    }

    @Override
    @Transactional
    public PickupProofEntity createProof(UUID orderId, PickupMethod method, String delegatedTo,
                                         String namedRecipient) {
        TrustContext ctx = TrustContextHolder.require();

        // Generate the plaintext credential
        String plainCredential;
        if (method == PickupMethod.SMS_REFERENCE) {
            // Fail-closed: the SMS path requires a named recipient for the staff ID check.
            if (namedRecipient == null || namedRecipient.isBlank()) {
                throw new IllegalArgumentException(
                        "SMS_REFERENCE pickup requires a named recipient");
            }
            plainCredential = generateSmsReference();
        } else if (method == PickupMethod.OTP) {
            plainCredential = generateOtp(properties.getPickup().getOtpLength());
        } else {
            plainCredential = UUID.randomUUID().toString();
        }

        // Hash the credential for storage
        String credentialHash = hmacService.computeLookupHash(plainCredential);

        PickupProofEntity proof = new PickupProofEntity();
        proof.setDispenseOrderId(orderId);
        proof.setMethod(method);
        proof.setTokenHash(credentialHash);
        proof.setStatus(PickupStatus.PENDING);
        proof.setExpiresAt(OffsetDateTime.now().plusMinutes(properties.getPickup().getExpiryMinutes()));
        proof.setDelegatedTo(delegatedTo);
        proof.setNamedRecipient(namedRecipient);
        if (method == PickupMethod.SMS_REFERENCE) {
            proof.setSmsReferenceHash(credentialHash);
        }

        proof = proofRepository.save(proof);

        log.info("Pickup proof created: proofId={}, orderId={}, method={}, expiresAt={}",
                proof.getProofId(), orderId, method, proof.getExpiresAt());

        // Return the proof with the plaintext credential stashed in deviceFingerprint
        // (the only time the plain credential is accessible; callers read it from here)
        proof.setDeviceFingerprint(plainCredential);
        return proof;
    }

    @Override
    @Transactional
    public PickupProofEntity claimProof(String token, String deviceFingerprint) {
        return claimProof(token, deviceFingerprint, null, null, null, null);
    }

    @Override
    @Transactional
    public PickupProofEntity claimProof(String token, String deviceFingerprint,
                                        String biometricSubjectRef, String biometricModality,
                                        String biometricProbeBase64) {
        return claimProof(token, deviceFingerprint, null,
                biometricSubjectRef, biometricModality, biometricProbeBase64);
    }

    @Override
    @Transactional
    public PickupProofEntity claimProof(String token, String deviceFingerprint,
                                        String collectorIdentity,
                                        String biometricSubjectRef, String biometricModality,
                                        String biometricProbeBase64) {
        TrustContext ctx = TrustContextHolder.require();

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or blank");
        }

        String tokenHash = hmacService.computeLookupHash(token);

        PickupProofEntity proof = proofRepository.findByTokenHashAndStatus(tokenHash, PickupStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending pickup proof found for the provided token"));

        // Fail-closed: SMS_REFERENCE proofs carry a named-recipient ID-check
        // obligation and must be claimed through claimBySmsReference.
        if (proof.getMethod() == PickupMethod.SMS_REFERENCE) {
            throw new IllegalStateException(
                    "This pickup requires the SMS-reference collection path with recipient identity check");
        }

        // Check expiration
        if (proof.getExpiresAt() != null && proof.getExpiresAt().isBefore(OffsetDateTime.now())) {
            proof.setStatus(PickupStatus.EXPIRED);
            proofRepository.save(proof);
            throw new IllegalStateException("Pickup proof has expired");
        }

        // Optional live biometric collector-verification at collection. MATCH → mark the
        // collection biometric-verified; NO_MATCH → reject the collection (proof stays PENDING,
        // transaction rolls back); UNAVAILABLE / NO_REFERENCE → fall back to the token check.
        boolean biometricMatched = false;
        if (biometricProbeBase64 != null && !biometricProbeBase64.isBlank()
                && biometricSubjectRef != null && !biometricSubjectRef.isBlank()) {
            String modality = biometricModality != null && !biometricModality.isBlank()
                    ? biometricModality : "FINGERPRINT";
            var decision = biometricVerification.verify(biometricSubjectRef, modality, biometricProbeBase64);
            if (decision.isMatch()) {
                proof.setMethod(PickupMethod.BIOMETRIC);
                biometricMatched = true;
            } else if (!"UNAVAILABLE".equals(decision.result())
                    && !"NO_REFERENCE".equals(decision.result())) {
                throw new IllegalStateException(
                        "Biometric collector verification failed — collection rejected");
            }
            // UNAVAILABLE / NO_REFERENCE → proceed on the token check (unchanged).
        }

        // OF-B16: record who collected and at which server-assigned grade (§12.4).
        proof.setCollectorIdentity(resolveCollectorIdentity(collectorIdentity, proof, ctx));
        proof.setVerificationGrade(biometricMatched
                ? PickupVerificationGrade.BIOMETRIC_MATCH
                : (proof.getDelegatedTo() != null && !proof.getDelegatedTo().isBlank()
                        ? PickupVerificationGrade.DELEGATE_TOKEN_MATCH
                        : PickupVerificationGrade.TOKEN_MATCH));

        proof.setStatus(PickupStatus.CLAIMED);
        proof.setClaimedBy(ctx.actorId());
        proof.setClaimedAt(OffsetDateTime.now());
        proof.setDeviceFingerprint(deviceFingerprint);
        proof = proofRepository.save(proof);

        publishEvent("PICKUP", proof.getProofId().toString(),
                "PICKUP_CLAIMED", proof, ctx.tenantId());

        log.info("Pickup proof claimed: proofId={}, orderId={}, claimedBy={}, grade={}",
                proof.getProofId(), proof.getDispenseOrderId(), ctx.actorId(),
                proof.getVerificationGrade());
        return proof;
    }

    @Override
    @Transactional(readOnly = true)
    public PickupProofEntity resolveSmsReference(String reference) {
        TrustContextHolder.require();
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Reference must not be null or blank");
        }
        String hash = hmacService.computeLookupHash(normaliseReference(reference));
        return proofRepository.findBySmsReferenceHashAndStatus(hash, PickupStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending pickup found for the provided reference"));
    }

    @Override
    @Transactional
    public PickupProofEntity claimBySmsReference(String reference, String collectorIdentity,
                                                 String deviceFingerprint) {
        TrustContext ctx = TrustContextHolder.require();

        // Fail-closed: the SMS path is exactly reference + verified collector identity.
        if (collectorIdentity == null || collectorIdentity.isBlank()) {
            throw new IllegalArgumentException(
                    "Collector identity is required for SMS-reference collection");
        }
        PickupProofEntity proof = resolveSmsReference(reference);

        if (proof.getExpiresAt() != null && proof.getExpiresAt().isBefore(OffsetDateTime.now())) {
            proof.setStatus(PickupStatus.EXPIRED);
            proofRepository.save(proof);
            throw new IllegalStateException("Pickup proof has expired");
        }

        // Named-recipient check: staff verified the collector's identity document
        // against the named recipient — the entered identity must match.
        if (proof.getNamedRecipient() != null
                && !proof.getNamedRecipient().trim().equalsIgnoreCase(collectorIdentity.trim())) {
            throw new IllegalStateException(
                    "Collector identity does not match the named recipient — collection rejected");
        }

        proof.setCollectorIdentity(collectorIdentity.trim());
        proof.setVerificationGrade(PickupVerificationGrade.SMS_REFERENCE_ID_CHECK);
        proof.setStatus(PickupStatus.CLAIMED);
        proof.setClaimedBy(ctx.actorId());
        proof.setClaimedAt(OffsetDateTime.now());
        proof.setDeviceFingerprint(deviceFingerprint);
        proof = proofRepository.save(proof);

        publishEvent("PICKUP", proof.getProofId().toString(),
                "PICKUP_CLAIMED", proof, ctx.tenantId());

        log.info("Pickup claimed by SMS reference: proofId={}, orderId={}, collector={}",
                proof.getProofId(), proof.getDispenseOrderId(), collectorIdentity);
        return proof;
    }

    /**
     * Scheduled job to expire unclaimed pickup proofs.
     *
     * <p>Runs every 5 minutes and marks all PENDING proofs with an
     * {@code expiresAt} before the current time as EXPIRED.</p>
     *
     * <p>OF-B16 escalation: an uncollected episode whose proof expires is
     * cancelled — a READY episode cancels cleanly (claim released as the
     * pre-dispense compensation); a DISPENSED / PARTIALLY_DISPENSED episode
     * gets its stock movements reversed (return-to-stock) before cancellation,
     * with the consumed claim flagged for ops review (never silently
     * restored). Each escalation emits {@code PICKUP_EXPIRED_RETURN}.</p>
     */
    @Scheduled(fixedDelayString = "${pharmacy.pickup.expire-check-interval-ms:300000}")
    @Transactional
    public void expireProofs() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PickupProofEntity> expired =
                proofRepository.findByStatusAndExpiresAtBefore(PickupStatus.PENDING, now);

        if (expired.isEmpty()) {
            return;
        }

        int count = 0;
        for (PickupProofEntity proof : expired) {
            proof.setStatus(PickupStatus.EXPIRED);
            proofRepository.save(proof);
            count++;

            try {
                escalateUncollected(proof);
            } catch (Exception e) {
                // The sweep must never die on one episode; the proof is already
                // EXPIRED and the next honest state is ops-visible via logs.
                log.error("Uncollected-pickup escalation failed for proof {} (order {}): {}",
                        proof.getProofId(), proof.getDispenseOrderId(), e.getMessage(), e);
            }
        }

        log.info("Expired {} unclaimed pickup proofs", count);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * OF-B16: escalate an uncollected episode after proof expiry — cancel the
     * episode, return stock where it had moved, emit the coded outbox event.
     */
    private void escalateUncollected(PickupProofEntity proof) {
        Optional<DispenseOrderEntity> maybeOrder = orderRepository.findById(proof.getDispenseOrderId());
        if (maybeOrder.isEmpty()) {
            log.warn("Expired pickup proof {} references missing order {}",
                    proof.getProofId(), proof.getDispenseOrderId());
            return;
        }
        DispenseOrderEntity order = maybeOrder.get();
        DispenseStatus status = order.getStatus();

        boolean cancellable = status == DispenseStatus.READY
                || status == DispenseStatus.DISPENSED
                || status == DispenseStatus.PARTIALLY_DISPENSED;
        if (!cancellable) {
            // Terminal or still-in-progress states: nothing to escalate (fail-closed —
            // we never guess); the expiry itself is already recorded on the proof.
            log.info("Expired proof {}: order {} in status {} — no escalation",
                    proof.getProofId(), order.getOrderId(), status);
            return;
        }

        // The sweep runs without a request context — synthesize the system actor
        // bound to the order's own tenant/facility (mirrors OrosConsumer).
        TrustContext systemCtx = new TrustContext(
                order.getTenantId(),
                "PHARMACY-SYSTEM",
                "SYSTEM",
                "PHARMACY_DISPENSE",
                null,
                UUID.randomUUID(),
                order.getFacilityId(),
                order.getWorkspaceId(),
                null,
                null);
        TrustContextHolder.set(systemCtx);
        try {
            // Stock return: reverse any recorded dispense movements (no-op for a
            // READY episode whose stock never moved — CC-12 keeps ledger truth).
            List<StockMovementEntity> reversed = stockLedgerService.reverseMovements(
                    "DISPENSE_ORDER", order.getOrderId().toString(), "PICKUP_EXPIRED_RETURN");

            // Cancel the episode. For a READY episode the engine releases the
            // prescription claim (pre-dispense compensation, OF-B12); for a
            // dispensed episode the claim stays consumed and the event below
            // flags it for ops/prescriber review.
            dispenseEngine.cancelOrder(order.getOrderId());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("proofId", proof.getProofId() != null ? proof.getProofId().toString() : null);
            payload.put("orderId", order.getOrderId().toString());
            payload.put("orosOrderId", order.getOrosOrderId());
            payload.put("priorStatus", status.name());
            payload.put("stockMovementsReversed", reversed != null ? reversed.size() : 0);
            payload.put("prescriptionClaimId", order.getPrescriptionClaimId() != null
                    ? order.getPrescriptionClaimId().toString() : null);
            payload.put("requiresClaimReview",
                    order.getPrescriptionClaimId() != null && status != DispenseStatus.READY);
            publishEvent("PICKUP", proof.getProofId().toString(),
                    "PICKUP_EXPIRED_RETURN", payload, order.getTenantId());

            log.info("Uncollected pickup escalated: proofId={}, orderId={}, priorStatus={}, reversed={}",
                    proof.getProofId(), order.getOrderId(), status,
                    reversed != null ? reversed.size() : 0);
        } finally {
            TrustContextHolder.clear();
        }
    }

    private static String resolveCollectorIdentity(String collectorIdentity,
                                                   PickupProofEntity proof, TrustContext ctx) {
        if (collectorIdentity != null && !collectorIdentity.isBlank()) {
            return collectorIdentity.trim();
        }
        if (proof.getDelegatedTo() != null && !proof.getDelegatedTo().isBlank()) {
            return proof.getDelegatedTo();
        }
        return ctx.actorId();
    }

    /**
     * Generate a numeric OTP of the specified length using SecureRandom.
     */
    private String generateOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Generate the opaque SMS reference (unambiguous alphabet, no clinical content). */
    private String generateSmsReference() {
        StringBuilder sb = new StringBuilder(SMS_REFERENCE_LENGTH);
        for (int i = 0; i < SMS_REFERENCE_LENGTH; i++) {
            sb.append(SMS_REFERENCE_ALPHABET[SECURE_RANDOM.nextInt(SMS_REFERENCE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normaliseReference(String reference) {
        return reference.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
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
