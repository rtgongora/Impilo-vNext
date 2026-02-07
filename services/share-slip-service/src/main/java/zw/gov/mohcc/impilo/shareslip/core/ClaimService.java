package zw.gov.mohcc.impilo.shareslip.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shareslip.config.ShareSlipProperties;
import zw.gov.mohcc.impilo.shareslip.dto.ClaimResult;
import zw.gov.mohcc.impilo.shareslip.dto.ClaimShareRequest;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareAuditEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareAuditRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareLinkRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for claiming shared document links.
 *
 * Handles the public claim flow:
 * 1. Validate share token exists and is ACTIVE
 * 2. Check expiry
 * 3. Check max claims not exceeded
 * 4. Verify OTP (constant-time comparison via HmacService)
 * 5. Check OTP attempt limits
 * 6. Generate signed URLs from landela-adapter
 * 7. Update claim count and record audit trail
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ShareLinkRepository shareLinkRepository;
    private final ShareAuditRepository shareAuditRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final OtpService otpService;
    private final ShareSlipProperties properties;
    private final RestTemplate restTemplate;

    public ClaimService(ShareLinkRepository shareLinkRepository,
                        ShareAuditRepository shareAuditRepository,
                        EventOutboxRepository eventOutboxRepository,
                        OtpService otpService,
                        ShareSlipProperties properties,
                        RestTemplate restTemplate) {
        this.shareLinkRepository = shareLinkRepository;
        this.shareAuditRepository = shareAuditRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.otpService = otpService;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * Attempt to claim a shared document link.
     *
     * @param request the claim request containing share token, OTP, and device fingerprint
     * @return the claim result with document IDs and signed URLs on success
     */
    @Transactional
    public ClaimResult claimShare(ClaimShareRequest request) {
        // Find the share link by token
        ShareLinkEntity entity = shareLinkRepository.findByShareToken(request.shareToken())
                .orElse(null);

        if (entity == null) {
            log.warn("Claim attempt with invalid token: {}", maskToken(request.shareToken()));
            return ClaimResult.failure("Invalid or unknown share token");
        }

        // Check link status
        if (!"ACTIVE".equals(entity.getStatus())) {
            log.info("Claim attempt on non-active link: linkId={}, status={}",
                    entity.getLinkId(), entity.getStatus());
            return ClaimResult.failure("This share link is no longer active (status: " + entity.getStatus() + ")");
        }

        // Check expiry
        if (entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            entity.setStatus("EXPIRED");
            shareLinkRepository.save(entity);
            recordAudit(entity.getLinkId(), "EXPIRED", null, request.deviceFingerprint(), null, "{}");
            log.info("Share link expired during claim: linkId={}", entity.getLinkId());
            return ClaimResult.failure("This share link has expired");
        }

        // Check max claims
        if (entity.getClaimCount() >= entity.getMaxClaims()) {
            log.info("Max claims exceeded: linkId={}, claims={}/{}",
                    entity.getLinkId(), entity.getClaimCount(), entity.getMaxClaims());
            return ClaimResult.failure("Maximum number of claims reached for this share link");
        }

        // Check OTP attempts
        if (entity.getOtpAttempts() >= properties.getOtp().getMaxAttempts()) {
            entity.setStatus("REVOKED");
            entity.setRevokedAt(OffsetDateTime.now());
            shareLinkRepository.save(entity);
            recordAudit(entity.getLinkId(), "REVOKED", null, request.deviceFingerprint(), null,
                    "{\"reason\":\"max_otp_attempts_exceeded\"}");
            log.warn("Share link revoked due to max OTP attempts: linkId={}", entity.getLinkId());
            return ClaimResult.failure("Too many OTP attempts. This share link has been revoked for security.");
        }

        // Verify OTP (for OTP verification method)
        if ("OTP".equals(entity.getVerificationMethod())) {
            if (request.otp() == null || request.otp().isBlank()) {
                return ClaimResult.failure("OTP is required for this share link");
            }

            entity.setOtpAttempts(entity.getOtpAttempts() + 1);
            shareLinkRepository.save(entity);

            if (!otpService.verifyOtp(request.otp(), entity.getOtpHash())) {
                int remaining = properties.getOtp().getMaxAttempts() - entity.getOtpAttempts();
                recordAudit(entity.getLinkId(), "OTP_FAILED", null, request.deviceFingerprint(), null,
                        "{\"attemptsRemaining\":" + remaining + "}");
                log.info("OTP verification failed: linkId={}, attemptsRemaining={}",
                        entity.getLinkId(), remaining);
                return ClaimResult.failure("Invalid OTP. " + remaining + " attempt(s) remaining.");
            }

            recordAudit(entity.getLinkId(), "OTP_VERIFIED", null, request.deviceFingerprint(), null, "{}");
        }

        // Generate signed URLs from landela-adapter
        List<String> signedUrls = generateSignedUrls(entity.getDocumentIds());

        // Update claim state
        entity.setClaimCount(entity.getClaimCount() + 1);
        entity.setClaimedAt(OffsetDateTime.now());
        entity.setClaimedBy(request.deviceFingerprint());
        entity.setClaimedDeviceFingerprint(request.deviceFingerprint());

        if (entity.getClaimCount() >= entity.getMaxClaims()) {
            entity.setStatus("CLAIMED");
        }

        shareLinkRepository.save(entity);

        // Record audit
        recordAudit(entity.getLinkId(), "CLAIMED", null, request.deviceFingerprint(), null,
                "{\"claimCount\":" + entity.getClaimCount() + ",\"maxClaims\":" + entity.getMaxClaims() + "}");

        // Outbox event
        recordOutboxEvent("SHARE_LINK", entity.getLinkId().toString(),
                "share.link.claimed", buildClaimEventPayload(entity));

        log.info("Share link claimed successfully: linkId={}, claimCount={}/{}",
                entity.getLinkId(), entity.getClaimCount(), entity.getMaxClaims());

        return ClaimResult.success(entity.getDocumentIds(), signedUrls);
    }

    /**
     * Generate signed download URLs for the given document IDs
     * by calling the landela-adapter service.
     */
    private List<String> generateSignedUrls(List<UUID> documentIds) {
        List<String> urls = new ArrayList<>();
        String baseUrl = properties.getLandela().getBaseUrl();

        for (UUID docId : documentIds) {
            try {
                // Call landela-adapter for signed URL
                String signedUrl = restTemplate.getForObject(
                        baseUrl + "/v1/internal/documents/{docId}/signed-url",
                        String.class,
                        docId
                );
                urls.add(signedUrl != null ? signedUrl : baseUrl + "/v1/internal/documents/" + docId + "/download");
            } catch (Exception e) {
                log.warn("Failed to generate signed URL for document {}: {}", docId, e.getMessage());
                // Fallback to direct download URL
                urls.add(baseUrl + "/v1/internal/documents/" + docId + "/download");
            }
        }

        return urls;
    }

    private void recordAudit(UUID linkId, String action, String actorId,
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

    private void recordOutboxEvent(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        eventOutboxRepository.save(event);
    }

    private String buildClaimEventPayload(ShareLinkEntity entity) {
        return """
                {"linkId":"%s","tenantId":"%s","subjectType":"%s","subjectId":"%s","status":"%s","claimCount":%d,"maxClaims":%d,"claimedAt":"%s"}"""
                .formatted(
                        entity.getLinkId(),
                        entity.getTenantId(),
                        entity.getSubjectType(),
                        entity.getSubjectId(),
                        entity.getStatus(),
                        entity.getClaimCount(),
                        entity.getMaxClaims(),
                        entity.getClaimedAt()
                );
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
