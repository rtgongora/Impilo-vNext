package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.RemittanceStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RemittanceTokenRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Remittance slip issuance and claim service.
 *
 * Generates secure remittance tokens (token + OTP) that can be used to claim
 * a payment at a facility. Only hashes are stored; plaintext values are returned
 * once at creation and never persisted.
 */
@Service
public class RemittanceService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RemittanceTokenRepository tokenRepository;
    private final PaymentIntentRepository intentRepository;
    private final PaymentIntentService intentService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final HmacService hmacService;
    private final MushexProperties properties;

    public RemittanceService(RemittanceTokenRepository tokenRepository,
                             PaymentIntentRepository intentRepository,
                             PaymentIntentService intentService,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper,
                             MushexProperties properties) {
        this.tokenRepository = tokenRepository;
        this.intentRepository = intentRepository;
        this.intentService = intentService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.hmacService = new HmacService(properties.getHmacPepper());
    }

    /**
     * Issue a remittance slip for a payment intent.
     * Generates a random token and OTP, hashes both, and stores only the hashes.
     * Returns the plaintext token and OTP to the caller (one-time display).
     *
     * @param intentId the payment intent to issue a slip for
     * @return a map containing "tokenId", "token" (plaintext), "otp" (plaintext), and "expiresAt"
     */
    @Transactional
    public Map<String, String> issueSlip(String intentId) {
        TrustContext ctx = TrustContextHolder.require();

        PaymentIntentEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        // Rate limit: max N slips per intent per hour
        int rateLimitPerMinute = properties.getRemittance().getRateLimitPerMinute();
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long recentCount = tokenRepository.countByIntentIdAndCreatedAtAfter(intentId, oneHourAgo);
        if (recentCount >= rateLimitPerMinute) {
            throw new IllegalStateException(
                    "Rate limit exceeded: maximum " + rateLimitPerMinute + " slips per intent per hour");
        }

        // Generate plaintext token and OTP
        String tokenPlaintext = generateSecureToken(32);
        String otpPlaintext = generateNumericOtp(properties.getRemittance().getOtpLength());

        // Hash both using HMAC
        String tokenHash = hmacService.computeLookupHash(tokenPlaintext);
        String otpHash = hmacService.computeLookupHash(otpPlaintext);

        // Compute expiry
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusMinutes(properties.getRemittance().getExpiryMinutes());

        // Persist token entity (hashes only)
        RemittanceTokenEntity token = new RemittanceTokenEntity();
        token.setId(UlidGenerator.generate());
        token.setIntentId(intentId);
        token.setTokenHash(tokenHash);
        token.setOtpHash(otpHash);
        token.setExpiresAt(expiresAt);
        token.setStatus(RemittanceStatus.ACTIVE);

        token = tokenRepository.save(token);

        log.info("Issued remittance slip: tokenId={}, intentId={}, expiresAt={}",
                token.getId(), intentId, expiresAt);

        publishEvent("REMITTANCE", token.getId(), "REMITTANCE_ISSUED",
                Map.of(
                        "tokenId", token.getId(),
                        "intentId", intentId,
                        "expiresAt", expiresAt.toString()
                ),
                ctx.tenantId());

        // Return plaintext values (one-time only)
        return Map.of(
                "tokenId", token.getId(),
                "token", tokenPlaintext,
                "otp", otpPlaintext,
                "expiresAt", expiresAt.toString()
        );
    }

    /**
     * Claim a remittance slip by providing the plaintext token and OTP.
     * Verifies both hashes, checks expiry and claimed status, then marks
     * the slip as CLAIMED and transitions the intent to PENDING.
     *
     * @param tokenPlaintext the plaintext token from the slip
     * @param otpPlaintext   the plaintext OTP from the slip
     * @param claimMeta      JSON metadata about the claim (e.g. facility, agent)
     * @return the claimed remittance token entity
     */
    @Transactional
    public RemittanceTokenEntity claimSlip(String tokenPlaintext, String otpPlaintext, String claimMeta) {
        TrustContext ctx = TrustContextHolder.require();

        // Hash the provided token to look up
        String tokenHash = hmacService.computeLookupHash(tokenPlaintext);

        RemittanceTokenEntity token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid remittance token"));

        // Verify OTP hash
        String otpHash = hmacService.computeLookupHash(otpPlaintext);
        if (!constantTimeEquals(otpHash, token.getOtpHash())) {
            throw new IllegalArgumentException("Invalid OTP for remittance token");
        }

        // Check expiry
        if (OffsetDateTime.now().isAfter(token.getExpiresAt())) {
            throw new IllegalStateException("Remittance token has expired");
        }

        // Check not already claimed or revoked
        if (token.getStatus() != RemittanceStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Remittance token is not claimable, current status: " + token.getStatus());
        }

        // Mark as claimed
        token.setStatus(RemittanceStatus.CLAIMED);
        token.setClaimedAt(OffsetDateTime.now());
        token.setClaimMeta(claimMeta);
        token = tokenRepository.save(token);

        log.info("Remittance slip claimed: tokenId={}, intentId={}", token.getId(), token.getIntentId());

        // Transition intent to PENDING
        PaymentIntentEntity intent = intentRepository.findById(token.getIntentId()).orElse(null);
        if (intent != null && intent.getStatus() == IntentStatus.CREATED) {
            intentService.transitionStatus(intent.getIntentId(), IntentStatus.PENDING);
        }

        publishEvent("REMITTANCE", token.getId(), "REMITTANCE_CLAIMED",
                Map.of(
                        "tokenId", token.getId(),
                        "intentId", token.getIntentId(),
                        "claimedAt", token.getClaimedAt().toString()
                ),
                ctx.tenantId());

        return token;
    }

    /**
     * Revoke a remittance slip, preventing it from being claimed.
     *
     * @param tokenId the remittance token ID to revoke
     * @return the revoked token entity
     */
    @Transactional
    public RemittanceTokenEntity revokeSlip(String tokenId) {
        TrustContext ctx = TrustContextHolder.require();

        RemittanceTokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Remittance token not found: " + tokenId));

        if (token.getStatus() != RemittanceStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot revoke token in status: " + token.getStatus());
        }

        token.setStatus(RemittanceStatus.REVOKED);
        token = tokenRepository.save(token);

        log.info("Remittance slip revoked: tokenId={}, intentId={}", tokenId, token.getIntentId());

        publishEvent("REMITTANCE", tokenId, "REMITTANCE_REVOKED",
                Map.of(
                        "tokenId", tokenId,
                        "intentId", token.getIntentId()
                ),
                ctx.tenantId());

        return token;
    }

    /**
     * Generate a cryptographically secure random hex token.
     */
    private String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteLength * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Generate a numeric OTP of the specified length.
     */
    private String generateNumericOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
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
