package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkCreatedResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkValidationResult;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ShareLinkExpiredException;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ShareLinkNotFoundException;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ShareLinkEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ShareLinkRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing short-lived share links for patient-delegated data sharing.
 *
 * <p>Share links allow patients to delegate temporary access to their data.
 * Tokens are generated as secure random bytes, and only the SHA-256 hash is persisted.
 * The plaintext token is returned to the creator exactly once and never stored.</p>
 */
@Service
public class ShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkService.class);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ShareLinkRepository shareLinkRepo;
    private final EventOutboxRepository outboxRepo;
    private final ConsentProperties properties;
    private final ObjectMapper objectMapper;

    public ShareLinkService(ShareLinkRepository shareLinkRepo,
                             EventOutboxRepository outboxRepo,
                             ConsentProperties properties,
                             ObjectMapper objectMapper) {
        this.shareLinkRepo = shareLinkRepo;
        this.outboxRepo = outboxRepo;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new share link. Returns the plaintext token exactly once.
     *
     * @param request   the share link creation request
     * @param createdBy the identity of the actor creating the link
     * @return the created share link with plaintext token
     */
    @Transactional
    public ShareLinkCreatedResponse create(CreateShareLinkRequest request, String createdBy) {
        log.info("Creating share link for patient={} by actor={} scope={} purpose={}",
                request.patientRef(), createdBy, request.scope(), request.purpose());

        // Validate expiration does not exceed max allowed hours
        int expiresInHours = request.expiresInHours();
        if (expiresInHours > properties.getMaxShareLinkHours()) {
            throw new IllegalArgumentException(
                    "Share link expiration cannot exceed " + properties.getMaxShareLinkHours() + " hours");
        }

        // Generate secure random token
        String plainToken = generateToken();
        String tokenHash = hashToken(plainToken);

        // Build entity
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setTenantId(request.tenantId());
        entity.setPatientRef(request.patientRef());
        entity.setCreatedBy(createdBy);
        entity.setScope(request.scope());
        entity.setPurpose(request.purpose());
        entity.setTokenHash(tokenHash);
        entity.setMaxUses(request.maxUses());
        entity.setUseCount(0);
        entity.setExpiresAt(Instant.now().plus(expiresInHours, ChronoUnit.HOURS));

        entity = shareLinkRepo.save(entity);

        // Outbox event
        createOutboxEvent(entity, "ShareLinkCreated");

        log.info("Created share link id={} for patient={} expires={}",
                entity.getId(), entity.getPatientRef(), entity.getExpiresAt());

        return new ShareLinkCreatedResponse(
                entity.getId(),
                plainToken,
                entity.getExpiresAt(),
                entity.getMaxUses()
        );
    }

    /**
     * Validates a share link token and consumes one use.
     * Returns the share link details if valid, or throws if expired/revoked/exhausted.
     *
     * @param token the plaintext token to validate
     * @return validation result with share link details
     */
    @Transactional
    public ShareLinkValidationResult validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            return ShareLinkValidationResult.failure("Token is required");
        }

        String tokenHash = hashToken(token);
        ShareLinkEntity entity = shareLinkRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ShareLinkNotFoundException("Share link not found for provided token"));

        // Check validity
        if (entity.getRevokedAt() != null) {
            log.warn("Share link id={} has been revoked", entity.getId());
            return ShareLinkValidationResult.failure("Share link has been revoked");
        }

        if (Instant.now().isAfter(entity.getExpiresAt())) {
            log.warn("Share link id={} has expired at {}", entity.getId(), entity.getExpiresAt());
            return ShareLinkValidationResult.failure("Share link has expired");
        }

        if (entity.getUseCount() >= entity.getMaxUses()) {
            log.warn("Share link id={} has exceeded max uses ({}/{})",
                    entity.getId(), entity.getUseCount(), entity.getMaxUses());
            return ShareLinkValidationResult.failure("Share link has exceeded maximum uses");
        }

        // Consume one use
        entity.setUseCount(entity.getUseCount() + 1);
        shareLinkRepo.save(entity);

        int remainingUses = entity.getMaxUses() - entity.getUseCount();
        log.info("Share link id={} consumed (remaining={})", entity.getId(), remainingUses);

        // Outbox event
        createOutboxEvent(entity, "ShareLinkConsumed");

        return ShareLinkValidationResult.success(
                entity.getPatientRef(),
                entity.getScope(),
                entity.getPurpose(),
                remainingUses
        );
    }

    /**
     * Revokes a share link by its ID.
     */
    @Transactional
    public void revoke(UUID id, String actorId) {
        log.info("Revoking share link id={} by actor={}", id, actorId);

        ShareLinkEntity entity = shareLinkRepo.findById(id)
                .orElseThrow(() -> new ShareLinkNotFoundException("Share link not found: " + id));

        if (entity.getRevokedAt() != null) {
            throw new ShareLinkExpiredException("Share link already revoked: " + id);
        }

        entity.setRevokedAt(Instant.now());
        shareLinkRepo.save(entity);

        // Outbox event
        createOutboxEvent(entity, "ShareLinkRevoked");

        log.info("Revoked share link id={}", id);
    }

    /**
     * Retrieves a share link by its ID for display.
     */
    @Transactional(readOnly = true)
    public ShareLinkDto findById(UUID id) {
        ShareLinkEntity entity = shareLinkRepo.findById(id)
                .orElseThrow(() -> new ShareLinkNotFoundException("Share link not found: " + id));
        return toDto(entity);
    }

    // --- Internal helpers ---

    /**
     * Generates a cryptographically secure random token, URL-safe Base64 encoded.
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a token using SHA-256. Returns the hex-encoded hash.
     */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private ShareLinkDto toDto(ShareLinkEntity entity) {
        return new ShareLinkDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientRef(),
                entity.getCreatedBy(),
                entity.getScope(),
                entity.getPurpose(),
                entity.getMaxUses(),
                entity.getUseCount(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.isValid()
        );
    }

    private void createOutboxEvent(ShareLinkEntity link, String eventType) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("ShareLink");
        outbox.setAggregateId(link.getId().toString());
        outbox.setEventType(eventType);
        // Resolved from the share link itself; see ConsentCrudService for why this is
        // set explicitly rather than defaulted.
        outbox.setTenantId(link.getTenantId());
        outbox.setSubjectType("SHARE_LINK");
        outbox.setSubjectId(link.getId().toString());
        try {
            Map<String, Object> payload = Map.of(
                    "shareLinkId", link.getId().toString(),
                    "tenantId", link.getTenantId().toString(),
                    "patientRef", link.getPatientRef(),
                    "scope", link.getScope(),
                    "purpose", link.getPurpose(),
                    "timestamp", Instant.now().toString()
            );
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for share link={}: {}", link.getId(), e.getMessage());
            outbox.setPayload("{}");
        }
        outboxRepo.save(outbox);
    }
}
