package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.BiometricBindingEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderTokenEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.BiometricBindingRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderTokenRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * VA token lifecycle management.
 *
 * Handles issuance, rotation, verification, and recovery of Village-Agent
 * authentication tokens. Tokens follow the format VA-XXXXXXXXXX-C where X
 * are random digits and C is a Luhn check character.
 *
 * Security design:
 *   - Plaintext token is returned ONCE at issuance time; never stored
 *   - HMAC-SHA256 lookup_hash enables O(1) token lookup without plaintext
 *   - Argon2id verifier provides password-grade protection at rest
 *   - Recovery requires bound biometric verification
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final ProviderTokenRepository tokenRepository;
    private final ProviderRepository providerRepository;
    private final BiometricBindingRepository biometricBindingRepository;
    private final EventOutboxRepository outboxRepository;
    private final HmacService hmacService;
    private final Argon2IdService argon2IdService;

    public TokenService(ProviderTokenRepository tokenRepository,
                        ProviderRepository providerRepository,
                        BiometricBindingRepository biometricBindingRepository,
                        EventOutboxRepository outboxRepository,
                        HmacService hmacService,
                        Argon2IdService argon2IdService) {
        this.tokenRepository = tokenRepository;
        this.providerRepository = providerRepository;
        this.biometricBindingRepository = biometricBindingRepository;
        this.outboxRepository = outboxRepository;
        this.hmacService = hmacService;
        this.argon2IdService = argon2IdService;
    }

    /**
     * Result of token issuance containing the one-time plaintext token.
     */
    public record TokenIssuanceResult(String plainTextToken, String providerPublicId) {}

    // ---- Public API ----

    /**
     * Issue a new VA token for a provider.
     *
     * Generates a VA-XXXXXXXXXX-C token with Luhn check digit, stores the
     * HMAC lookup_hash and Argon2id verifier, and returns the plaintext
     * token ONE TIME. The plaintext is never persisted.
     *
     * @param providerPublicId the provider to issue a token for
     * @return the issuance result containing the plaintext token
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional
    public TokenIssuanceResult issueToken(String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing VA token: providerPublicId={}, actor={}", providerPublicId, ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        // Revoke any existing active tokens for this provider
        List<ProviderTokenEntity> activeTokens =
                tokenRepository.findByProviderIdAndStatus(provider.getId(), "ACTIVE");
        for (ProviderTokenEntity existing : activeTokens) {
            existing.setStatus("REVOKED");
            existing.setRevokedAt(Instant.now());
            existing.setRotationReason("Superseded by new issuance");
            tokenRepository.save(existing);
        }

        // Generate new token
        String plainToken = generateVaToken();
        String normalized = normalize(plainToken);
        String lookupHash = hmacService.computeLookupHash(normalized);
        String argon2Verifier = argon2IdService.hash(normalized);

        ProviderTokenEntity tokenEntity = new ProviderTokenEntity();
        tokenEntity.setProvider(provider);
        tokenEntity.setTenantId(ctx.tenantId());
        tokenEntity.setLookupHash(lookupHash);
        tokenEntity.setArgon2Verifier(argon2Verifier);
        tokenEntity.setStatus("ACTIVE");
        tokenEntity.setIssuedBy(ctx.actorId());
        tokenRepository.save(tokenEntity);

        log.info("VA token issued: providerPublicId={}, tokenId={}", providerPublicId, tokenEntity.getId());

        publishEvent("TOKEN", providerPublicId,
                "varapi.token.issued",
                String.format("{\"providerPublicId\":\"%s\",\"tokenId\":%d}",
                        providerPublicId, tokenEntity.getId()));

        return new TokenIssuanceResult(plainToken, providerPublicId);
    }

    /**
     * Rotate a provider's VA token.
     *
     * Marks the current active token as ROTATED and issues a new one.
     * The old token becomes immediately invalid.
     *
     * @param providerPublicId the provider whose token to rotate
     * @return the issuance result containing the new plaintext token
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional
    public TokenIssuanceResult rotateToken(String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Rotating VA token: providerPublicId={}, actor={}", providerPublicId, ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        // Find and mark existing active tokens as ROTATED
        ProviderTokenEntity previousToken = null;
        List<ProviderTokenEntity> activeTokens =
                tokenRepository.findByProviderIdAndStatus(provider.getId(), "ACTIVE");
        for (ProviderTokenEntity existing : activeTokens) {
            existing.setStatus("ROTATED");
            existing.setRotatedAt(Instant.now());
            existing.setRotationReason("Rotated by " + ctx.actorId());
            tokenRepository.save(existing);
            previousToken = existing;
        }

        // Generate new token
        String plainToken = generateVaToken();
        String normalized = normalize(plainToken);
        String lookupHash = hmacService.computeLookupHash(normalized);
        String argon2Verifier = argon2IdService.hash(normalized);

        ProviderTokenEntity tokenEntity = new ProviderTokenEntity();
        tokenEntity.setProvider(provider);
        tokenEntity.setTenantId(ctx.tenantId());
        tokenEntity.setLookupHash(lookupHash);
        tokenEntity.setArgon2Verifier(argon2Verifier);
        tokenEntity.setStatus("ACTIVE");
        tokenEntity.setIssuedBy(ctx.actorId());
        if (previousToken != null) {
            tokenEntity.setPreviousToken(previousToken);
        }
        tokenRepository.save(tokenEntity);

        log.info("VA token rotated: providerPublicId={}, newTokenId={}", providerPublicId, tokenEntity.getId());

        publishEvent("TOKEN", providerPublicId,
                "varapi.token.rotated",
                String.format("{\"providerPublicId\":\"%s\",\"newTokenId\":%d,\"previousTokenId\":%s}",
                        providerPublicId, tokenEntity.getId(),
                        previousToken != null ? previousToken.getId().toString() : "null"));

        return new TokenIssuanceResult(plainToken, providerPublicId);
    }

    /**
     * Verify a raw VA token and return the associated provider_public_id.
     *
     * Performs a two-step verification:
     *   1. HMAC lookup to find the token record (O(1))
     *   2. Argon2id verification to confirm the plaintext matches
     *
     * @param rawToken the plaintext VA token to verify
     * @return the provider_public_id if valid, or null if invalid
     */
    @Transactional(readOnly = true)
    public String verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            log.debug("Token verification failed: null or blank token");
            return null;
        }

        String normalized = normalize(rawToken);
        String lookupHash = hmacService.computeLookupHash(normalized);

        Optional<ProviderTokenEntity> tokenOpt = tokenRepository.findByLookupHash(lookupHash);
        if (tokenOpt.isEmpty()) {
            log.debug("Token verification failed: no matching lookup_hash");
            return null;
        }

        ProviderTokenEntity token = tokenOpt.get();
        if (!token.isActive()) {
            log.debug("Token verification failed: token status={}", token.getStatus());
            return null;
        }

        // Verify against Argon2id verifier
        boolean verified = argon2IdService.verify(normalized, token.getArgon2Verifier());
        if (!verified) {
            log.warn("Token verification failed: Argon2id mismatch for tokenId={}", token.getId());
            return null;
        }

        log.debug("Token verified: providerPublicId={}", token.getProvider().getProviderPublicId());
        return token.getProvider().getProviderPublicId();
    }

    /**
     * Initiate a token recovery process.
     *
     * Returns a generic response regardless of whether the provider exists
     * to prevent existence-leaking attacks (enumeration prevention).
     *
     * @param providerPublicId the provider requesting recovery
     * @return always returns a generic "recovery initiated" message
     */
    @Transactional(readOnly = true)
    public String startRecovery(String providerPublicId) {
        TrustContextHolder.require();
        log.info("Token recovery initiated: providerPublicId={}", providerPublicId);

        // Intentionally do not reveal whether the provider exists
        // The actual recovery flow requires biometric verification
        return "recovery initiated";
    }

    /**
     * Complete token recovery via biometric verification.
     *
     * Verifies that the provider has an active biometric binding matching
     * the provided reference. If valid, issues a new token.
     *
     * @param providerPublicId the provider recovering their token
     * @param biometricRef     the biometric reference to verify
     * @return the new token if verification succeeds, or null if it fails
     */
    @Transactional
    public TokenIssuanceResult verifyRecovery(String providerPublicId, String biometricRef) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Token recovery verification: providerPublicId={}, actor={}",
                providerPublicId, ctx.actorId());

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElse(null);

        if (provider == null) {
            log.debug("Recovery verification failed: provider not found");
            return null;
        }

        // Check for active biometric binding
        Optional<BiometricBindingEntity> bindingOpt =
                biometricBindingRepository.findByBiometricRefAndStatus(biometricRef, "ACTIVE");
        if (bindingOpt.isEmpty()) {
            log.debug("Recovery verification failed: no active biometric binding");
            return null;
        }

        BiometricBindingEntity binding = bindingOpt.get();
        if (!binding.getProvider().getId().equals(provider.getId())) {
            log.warn("Recovery verification failed: biometric binding does not match provider");
            return null;
        }

        log.info("Biometric verification passed, issuing recovery token: providerPublicId={}",
                providerPublicId);
        return issueToken(providerPublicId);
    }

    // ---- Token generation ----

    /**
     * Generate a VA token in the format VA-XXXXXXXXXX-C.
     * X = 10 random digits, C = Luhn check character.
     */
    private String generateVaToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        char check = computeLuhnCheck(digits.toString());
        return "VA-" + digits + check;
    }

    /**
     * Compute the Luhn mod-10 check digit for a string of digits.
     */
    private char computeLuhnCheck(String digits) {
        int sum = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if ((digits.length() - 1 - i) % 2 == 0) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
        }
        int check = (10 - (sum % 10)) % 10;
        return (char) ('0' + check);
    }

    /**
     * Normalize a token for consistent hashing.
     * Removes dashes, spaces; uppercases; trims.
     */
    private String normalize(String token) {
        return token.replace("-", "").replace(" ", "").toUpperCase().trim();
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
