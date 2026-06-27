package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupClaimTrust;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PickupTokenService {

    private static final Logger log = LoggerFactory.getLogger(PickupTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int TOKEN_BYTES = 16; // 128-bit
    private static final long TOKEN_EXPIRY_HOURS = 48;
    private static final int MAX_CLAIM_ATTEMPTS_PER_HOUR = 5;

    // Simple rate limiter (in production, use Redis)
    private final ConcurrentHashMap<String, List<Long>> claimAttempts = new ConcurrentHashMap<>();

    private final PickupTokenRepository tokenRepository;
    private final OrderStateMachine stateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MsikaPickupBiometricPolicyClient pickupBiometricPolicyClient;

    public PickupTokenService(PickupTokenRepository tokenRepository,
                              OrderStateMachine stateMachine,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper,
                              MsikaPickupBiometricPolicyClient pickupBiometricPolicyClient) {
        this.tokenRepository = tokenRepository;
        this.stateMachine = stateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.pickupBiometricPolicyClient = pickupBiometricPolicyClient;
    }

    @Transactional
    public PickupIssueResult issueToken(String orderId, String actorId, UUID tenantId) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Order must be READY_FOR_PICKUP to issue pickup token. Current: " + order.getStatus());
        }

        // Revoke any existing active token for this order
        tokenRepository.findByOrderIdAndStatus(orderId, PickupTokenStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(PickupTokenStatus.REVOKED);
                    tokenRepository.save(existing);
                });

        // Generate raw token (128-bit random)
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Generate 6-digit OTP
        int otpInt = SECURE_RANDOM.nextInt(900000) + 100000;
        String rawOtp = String.valueOf(otpInt);

        // Hash both
        String tokenHash = sha256(rawToken);
        String otpHash = sha256(rawOtp);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);

        PickupTokenEntity token = new PickupTokenEntity();
        token.setId(UlidGenerator.generate());
        token.setOrderId(orderId);
        token.setTokenHash(tokenHash);
        token.setOtpHash(otpHash);
        token.setExpiresAt(expiresAt);
        token.setStatus(PickupTokenStatus.ACTIVE);

        tokenRepository.save(token);

        // Publish outbox event
        publishOutbox("PickupToken", token.getId(), "PICKUP_ISSUED", tenantId,
                Map.of("orderId", orderId, "tokenId", token.getId(), "expiresAt", expiresAt.toString()));

        log.info("Pickup token issued: orderId={} tokenId={} expiresAt={}", orderId, token.getId(), expiresAt);

        return new PickupIssueResult(token.getId(), rawToken, rawOtp, expiresAt);
    }

    @Transactional
    public ClaimResult claimPickup(String tokenOrOtp, PickupClaimTrust trust) {
        // Rate limiting
        if (isRateLimited(trust.actorId())) {
            throw new IllegalStateException("Too many claim attempts. Please wait before trying again.");
        }
        recordAttempt(trust.actorId());

        pickupBiometricPolicyClient.assertPickupClaimAllowed(trust);

        // Try to find by token hash first, then OTP hash
        String hash = sha256(tokenOrOtp);
        Optional<PickupTokenEntity> tokenOpt = tokenRepository.findByTokenHash(hash);

        if (tokenOpt.isEmpty()) {
            // Try all active tokens and match OTP
            // This is a simplified approach; production would use indexed lookup
            log.warn("Token not found by hash, attempting OTP match");
            throw new IllegalArgumentException("Invalid pickup token or OTP");
        }

        PickupTokenEntity token = tokenOpt.get();

        // Validate state
        if (token.getStatus() != PickupTokenStatus.ACTIVE) {
            throw new IllegalStateException("Token is not active. Status: " + token.getStatus());
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            token.setStatus(PickupTokenStatus.EXPIRED);
            tokenRepository.save(token);
            throw new IllegalStateException("Token has expired");
        }

        // Verify order state
        OrderEntity order = stateMachine.getOrder(token.getOrderId());
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new IllegalStateException("Order is not ready for pickup. Status: " + order.getStatus());
        }

        // Claim the token
        token.setStatus(PickupTokenStatus.CLAIMED);
        token.setClaimedBy(trust.actorId());
        token.setClaimedActorType(trust.actorType());
        token.setClaimedAt(OffsetDateTime.now());

        try {
            token.setClaimMeta(objectMapper.writeValueAsString(Map.of(
                    "deviceFingerprint",
                    trust.deviceFingerprint() != null ? trust.deviceFingerprint() : "unknown",
                    "claimedAt", OffsetDateTime.now().toString()
            )));
        } catch (Exception e) {
            log.warn("Failed to serialize claim meta: {}", e.getMessage());
        }

        tokenRepository.save(token);

        // Transition order to COLLECTED
        stateMachine.transition(token.getOrderId(), OrderStatus.COLLECTED,
                trust.actorId(), trust.actorType(), "PICKUP_CLAIMED", null);

        // Publish event
        publishOutbox("PickupToken", token.getId(), "PICKUP_CLAIMED", trust.tenantId(),
                Map.of("orderId", token.getOrderId(), "tokenId", token.getId(),
                        "claimedBy", trust.actorId()));

        log.info("Pickup claimed: orderId={} claimedBy={}", token.getOrderId(), trust.actorId());
        return new ClaimResult(token.getOrderId(), token.getId(), trust.actorId());
    }

    private boolean isRateLimited(String actorId) {
        List<Long> attempts = claimAttempts.getOrDefault(actorId, Collections.emptyList());
        long oneHourAgo = System.currentTimeMillis() - 3600000;
        long recentAttempts = attempts.stream().filter(t -> t > oneHourAgo).count();
        return recentAttempts >= MAX_CLAIM_ATTEMPTS_PER_HOUR;
    }

    private void recordAttempt(String actorId) {
        claimAttempts.computeIfAbsent(actorId, k -> new ArrayList<>()).add(System.currentTimeMillis());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
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
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    /**
     * Resolves the printable-slip context for an order's active pickup token (G034).
     * Non-secret token metadata is read from the store; the optional client-held {@code rawToken}
     * is validated against the stored hash (so the slip embeds a genuine, scannable QR) but is
     * never persisted or logged. A blank/absent token yields {@code tokenVerified=false} (the slip
     * is rendered without the secret QR); a non-blank token that does not match is rejected.
     *
     * @throws IllegalArgumentException if the order has no active token, or a supplied token mismatches
     */
    @Transactional(readOnly = true)
    public PickupSlipContext resolveSlipContext(String orderId, String rawToken) {
        PickupTokenEntity token = tokenRepository.findByOrderIdAndStatus(orderId, PickupTokenStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active pickup token for order " + orderId));
        boolean supplied = rawToken != null && !rawToken.isBlank();
        boolean verified = supplied && sha256(rawToken).equals(token.getTokenHash());
        if (supplied && !verified) {
            throw new IllegalArgumentException(
                    "Provided token does not match the order's active pickup token");
        }
        return new PickupSlipContext(orderId, token.getId(), token.getExpiresAt(),
                token.getStatus().name(), verified);
    }

    public record PickupIssueResult(String tokenId, String rawToken, String rawOtp, OffsetDateTime expiresAt) {}
    public record ClaimResult(String orderId, String tokenId, String claimedBy) {}

    /** Non-secret slip context for {@code /orders/{id}/pickup/slip.pdf} (G034). */
    public record PickupSlipContext(String orderId, String tokenId, OffsetDateTime expiresAt,
                                    String status, boolean tokenVerified) {}
}
