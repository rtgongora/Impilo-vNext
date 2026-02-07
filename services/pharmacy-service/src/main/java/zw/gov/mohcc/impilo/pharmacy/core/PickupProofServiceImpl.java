package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyProperties;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.PickupProofRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages OTP/QR pickup proof creation, claim, and expiration workflows.
 *
 * <p>When a dispense order is ready for pickup, a proof record is created
 * with either an OTP code (6-digit numeric) or a QR token (UUID-based).
 * The plaintext token is returned exactly once to the caller; only the
 * HMAC hash is stored for later verification.</p>
 *
 * <p>A scheduled job expires unclaimed proofs that have passed their
 * expiry time.</p>
 */
@Service
public class PickupProofServiceImpl implements PickupProofService {

    private static final Logger log = LoggerFactory.getLogger(PickupProofServiceImpl.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PickupProofRepository proofRepository;
    private final EventOutboxRepository outboxRepository;
    private final HmacService hmacService;
    private final PharmacyProperties properties;
    private final ObjectMapper objectMapper;

    public PickupProofServiceImpl(PickupProofRepository proofRepository,
                                  EventOutboxRepository outboxRepository,
                                  HmacService hmacService,
                                  PharmacyProperties properties,
                                  ObjectMapper objectMapper) {
        this.proofRepository = proofRepository;
        this.outboxRepository = outboxRepository;
        this.hmacService = hmacService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PickupProofEntity createProof(UUID orderId, PickupMethod method, String delegatedTo) {
        TrustContext ctx = TrustContextHolder.require();

        // Generate the plaintext token
        String plainToken;
        if (method == PickupMethod.OTP) {
            plainToken = generateOtp(properties.getPickup().getOtpLength());
        } else {
            plainToken = UUID.randomUUID().toString();
        }

        // Hash the token for storage
        String tokenHash = hmacService.computeLookupHash(plainToken);

        PickupProofEntity proof = new PickupProofEntity();
        proof.setDispenseOrderId(orderId);
        proof.setMethod(method);
        proof.setTokenHash(tokenHash);
        proof.setStatus(PickupStatus.PENDING);
        proof.setExpiresAt(OffsetDateTime.now().plusMinutes(properties.getPickup().getExpiryMinutes()));
        proof.setDelegatedTo(delegatedTo);

        proof = proofRepository.save(proof);

        log.info("Pickup proof created: proofId={}, orderId={}, method={}, expiresAt={}",
                proof.getProofId(), orderId, method, proof.getExpiresAt());

        // Return the proof with the plaintext token stashed in deviceFingerprint
        // (the only time the plain token is accessible; callers read it from here)
        proof.setDeviceFingerprint(plainToken);
        return proof;
    }

    @Override
    @Transactional
    public PickupProofEntity claimProof(String token, String deviceFingerprint) {
        TrustContext ctx = TrustContextHolder.require();

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or blank");
        }

        String tokenHash = hmacService.computeLookupHash(token);

        PickupProofEntity proof = proofRepository.findByTokenHashAndStatus(tokenHash, PickupStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending pickup proof found for the provided token"));

        // Check expiration
        if (proof.getExpiresAt() != null && proof.getExpiresAt().isBefore(OffsetDateTime.now())) {
            proof.setStatus(PickupStatus.EXPIRED);
            proofRepository.save(proof);
            throw new IllegalStateException("Pickup proof has expired");
        }

        proof.setStatus(PickupStatus.CLAIMED);
        proof.setClaimedBy(ctx.actorId());
        proof.setClaimedAt(OffsetDateTime.now());
        proof.setDeviceFingerprint(deviceFingerprint);
        proof = proofRepository.save(proof);

        publishEvent("PICKUP", proof.getProofId().toString(),
                "PICKUP_CLAIMED", proof, ctx.tenantId());

        log.info("Pickup proof claimed: proofId={}, orderId={}, claimedBy={}",
                proof.getProofId(), proof.getDispenseOrderId(), ctx.actorId());
        return proof;
    }

    /**
     * Scheduled job to expire unclaimed pickup proofs.
     *
     * <p>Runs every 5 minutes and marks all PENDING proofs with an
     * {@code expiresAt} before the current time as EXPIRED.</p>
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
        }

        log.info("Expired {} unclaimed pickup proofs", count);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

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
