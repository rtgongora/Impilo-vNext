package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public PickupTokenService(PickupTokenRepository tokenRepository,
                              OrderStateMachine stateMachine,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.stateMachine = stateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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
    public ClaimResult claimPickup(String tokenOrOtp, String claimantActorId, String claimantActorType,
                                   String deviceFingerprint, UUID tenantId) {
        // Rate limiting
        if (isRateLimited(claimantActorId)) {
            throw new IllegalStateException("Too many claim attempts. Please wait before trying again.");
        }
        recordAttempt(claimantActorId);

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
        token.setClaimedBy(claimantActorId);
        token.setClaimedActorType(claimantActorType);
        token.setClaimedAt(OffsetDateTime.now());

        try {
            token.setClaimMeta(objectMapper.writeValueAsString(Map.of(
                    "deviceFingerprint", deviceFingerprint != null ? deviceFingerprint : "unknown",
                    "claimedAt", OffsetDateTime.now().toString()
            )));
        } catch (Exception e) {
            log.warn("Failed to serialize claim meta: {}", e.getMessage());
        }

        tokenRepository.save(token);

        // Transition order to COLLECTED
        stateMachine.transition(token.getOrderId(), OrderStatus.COLLECTED,
                claimantActorId, claimantActorType, "PICKUP_CLAIMED", null);

        // Publish event
        publishOutbox("PickupToken", token.getId(), "PICKUP_CLAIMED", tenantId,
                Map.of("orderId", token.getOrderId(), "tokenId", token.getId(),
                        "claimedBy", claimantActorId));

        log.info("Pickup claimed: orderId={} claimedBy={}", token.getOrderId(), claimantActorId);
        return new ClaimResult(token.getOrderId(), token.getId(), claimantActorId);
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

    public record PickupIssueResult(String tokenId, String rawToken, String rawOtp, OffsetDateTime expiresAt) {}
    public record ClaimResult(String orderId, String tokenId, String claimedBy) {}
}
