package zw.gov.mohcc.impilo.shareslip.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.shareslip.config.ShareSlipProperties;
import zw.gov.mohcc.impilo.shareslip.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.shareslip.dto.ShareLinkResponse;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareAuditEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareAuditRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareLinkRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Core service for share link lifecycle management.
 *
 * Handles creation, retrieval, listing, and revocation of share links.
 * Each share link includes a unique token (32-byte hex), an HMAC-hashed OTP,
 * and expiry tracking. All mutations produce audit records and outbox events.
 */
@Service
public class ShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ShareLinkRepository shareLinkRepository;
    private final ShareAuditRepository shareAuditRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final OtpService otpService;
    private final ShareSlipProperties properties;

    public ShareLinkService(ShareLinkRepository shareLinkRepository,
                            ShareAuditRepository shareAuditRepository,
                            EventOutboxRepository eventOutboxRepository,
                            OtpService otpService,
                            ShareSlipProperties properties) {
        this.shareLinkRepository = shareLinkRepository;
        this.shareAuditRepository = shareAuditRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.otpService = otpService;
        this.properties = properties;
    }

    /**
     * Create a new share link with OTP protection.
     *
     * Generates a cryptographically secure share token (32 bytes, hex-encoded),
     * generates and hashes an OTP, stores the link, records an audit entry,
     * and queues an outbox event for Kafka publishing.
     *
     * @param request the creation request
     * @return the created share link response (includes plaintext OTP in logs for dev)
     */
    @Transactional
    public ShareLinkResponse createShareLink(CreateShareLinkRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        // Generate share token: 32 random bytes, hex-encoded = 64 chars
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String shareToken = HexFormat.of().formatHex(tokenBytes);

        // Determine expiry
        int expiryHours = request.expiryHours() != null
                ? Math.min(request.expiryHours(), properties.getLink().getMaxExpiryHours())
                : properties.getLink().getDefaultExpiryHours();

        // Determine verification method
        String verificationMethod = request.verificationMethod() != null
                ? request.verificationMethod()
                : "OTP";

        // Generate and hash OTP
        String otp = otpService.generateOtp(properties.getOtp().getLength());
        String otpHash = otpService.hashOtp(otp);

        // Log OTP in dev mode (sendOtp also logs it)
        otpService.sendOtp("SMS", "dev-console", otp);

        // Build entity
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setShareToken(shareToken);
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setSubjectName(request.subjectName());
        entity.setCreatedBy(ctx.actorId());
        entity.setPurpose(request.purpose());
        entity.setDocumentIds(request.documentIds());
        entity.setVerificationMethod(verificationMethod);
        entity.setOtpHash(otpHash);
        entity.setMaxClaims(request.maxClaims() != null ? request.maxClaims() : 1);
        entity.setExpiresAt(OffsetDateTime.now().plusHours(expiryHours));

        entity = shareLinkRepository.save(entity);

        // Record audit
        recordAudit(entity.getLinkId(), "CREATED", ctx.actorId(), null, null,
                "{\"verificationMethod\":\"" + verificationMethod + "\"}");

        // Outbox event
        recordOutboxEvent("SHARE_LINK", entity.getLinkId().toString(),
                "share.link.created", buildEventPayload(entity));

        log.info("Share link created: linkId={}, subjectType={}, subjectId={}, expiryHours={}",
                entity.getLinkId(), entity.getSubjectType(), entity.getSubjectId(), expiryHours);

        return toResponse(entity);
    }

    /**
     * Get a share link by its UUID link ID.
     *
     * @param linkId the link UUID
     * @return the share link response
     * @throws IllegalArgumentException if not found
     */
    @Transactional(readOnly = true)
    public ShareLinkResponse getShareLink(UUID linkId) {
        ShareLinkEntity entity = shareLinkRepository.findByLinkId(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found: " + linkId));
        return toResponse(entity);
    }

    /**
     * List share links by subject type and subject ID with pagination.
     *
     * @param subjectType the subject type filter
     * @param subjectId   the subject ID filter
     * @param pageable    pagination parameters
     * @return paged response of share links
     */
    @Transactional(readOnly = true)
    public PagedResponse<ShareLinkResponse> listShareLinks(String subjectType, String subjectId, Pageable pageable) {
        Page<ShareLinkEntity> page = shareLinkRepository
                .findBySubjectTypeAndSubjectId(subjectType, subjectId, pageable);

        return PagedResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    /**
     * Revoke a share link, preventing further claims.
     *
     * @param linkId the link UUID to revoke
     */
    @Transactional
    public void revokeShareLink(UUID linkId) {
        TrustContext ctx = TrustContextHolder.require();

        ShareLinkEntity entity = shareLinkRepository.findByLinkId(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found: " + linkId));

        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new IllegalStateException("Cannot revoke share link with status: " + entity.getStatus());
        }

        entity.setStatus("REVOKED");
        entity.setRevokedAt(OffsetDateTime.now());
        shareLinkRepository.save(entity);

        recordAudit(entity.getLinkId(), "REVOKED", ctx.actorId(), null, null, "{}");

        recordOutboxEvent("SHARE_LINK", entity.getLinkId().toString(),
                "share.link.revoked", buildEventPayload(entity));

        log.info("Share link revoked: linkId={}", linkId);
    }

    /**
     * Get the raw entity by link ID (used by PDF service and claim service).
     */
    @Transactional(readOnly = true)
    public ShareLinkEntity getShareLinkEntity(UUID linkId) {
        return shareLinkRepository.findByLinkId(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found: " + linkId));
    }

    // --- Internal helpers ---

    void recordAudit(UUID linkId, String action, String actorId,
                     String deviceFingerprint, String ipAddress, String details) {
        ShareAuditEntity audit = new ShareAuditEntity();
        audit.setLinkId(linkId);
        audit.setAction(action);
        audit.setActorId(actorId);
        audit.setDeviceFingerprint(deviceFingerprint);
        audit.setIpAddress(ipAddress);
        audit.setDetails(details);
        shareAuditRepository.save(audit);
    }

    void recordOutboxEvent(String aggregateType, String aggregateId,
                           String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        eventOutboxRepository.save(event);
    }

    ShareLinkResponse toResponse(ShareLinkEntity entity) {
        return new ShareLinkResponse(
                entity.getLinkId(),
                entity.getTenantId(),
                entity.getShareToken(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getPurpose(),
                entity.getDocumentIds(),
                entity.getVerificationMethod(),
                entity.getMaxClaims(),
                entity.getClaimCount(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private String buildEventPayload(ShareLinkEntity entity) {
        return """
                {"linkId":"%s","tenantId":"%s","subjectType":"%s","subjectId":"%s","status":"%s","expiresAt":"%s"}"""
                .formatted(
                        entity.getLinkId(),
                        entity.getTenantId(),
                        entity.getSubjectType(),
                        entity.getSubjectId(),
                        entity.getStatus(),
                        entity.getExpiresAt()
                );
    }
}
