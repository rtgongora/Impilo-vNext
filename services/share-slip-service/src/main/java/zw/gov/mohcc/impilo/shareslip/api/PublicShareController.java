package zw.gov.mohcc.impilo.shareslip.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shareslip.core.ClaimService;
import zw.gov.mohcc.impilo.shareslip.dto.ClaimResult;
import zw.gov.mohcc.impilo.shareslip.dto.ClaimShareRequest;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareLinkRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Public controller for share link claim and verification.
 *
 * These endpoints are unauthenticated (no OAuth2 token required) and are
 * intended for delegates who have received a share slip. They are protected
 * by OTP verification and should be rate-limited at the infrastructure layer
 * (Envoy or API gateway).
 *
 * Endpoints:
 *   POST  /v1/public/share/claim         -- claim a share link with OTP
 *   GET   /v1/public/share/verify/{token} -- check if a share link is valid
 */
@RestController
@RequestMapping("/v1/public/share")
public class PublicShareController {

    private static final Logger log = LoggerFactory.getLogger(PublicShareController.class);

    private final ClaimService claimService;
    private final ShareLinkRepository shareLinkRepository;

    public PublicShareController(ClaimService claimService,
                                 ShareLinkRepository shareLinkRepository) {
        this.claimService = claimService;
        this.shareLinkRepository = shareLinkRepository;
    }

    /**
     * Claim a share link with OTP verification.
     *
     * This is the primary public endpoint for delegates to retrieve shared
     * documents. Requires a valid share token and the correct OTP.
     */
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<ClaimResult>> claimShare(
            @Valid @RequestBody ClaimShareRequest request) {
        String correlationId = UUID.randomUUID().toString();

        log.info("Public claim attempt for token: {}...", maskToken(request.shareToken()));

        ClaimResult result = claimService.claimShare(request);

        if (result.success()) {
            return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
        } else {
            return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
        }
    }

    /**
     * Verify whether a share token is valid and active.
     *
     * Returns minimal information about the share link (no sensitive data).
     * Used by the UI to check if a scanned QR code is still valid before
     * prompting the user for OTP input.
     */
    @GetMapping("/verify/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyShareToken(
            @PathVariable String token) {
        String correlationId = UUID.randomUUID().toString();

        ShareLinkEntity entity = shareLinkRepository.findByShareToken(token).orElse(null);

        if (entity == null) {
            Map<String, Object> result = Map.of(
                    "valid", false,
                    "message", "Share link not found"
            );
            return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
        }

        boolean isExpired = entity.getExpiresAt().isBefore(OffsetDateTime.now());
        boolean isActive = "ACTIVE".equals(entity.getStatus()) && !isExpired;
        boolean claimsAvailable = entity.getClaimCount() < entity.getMaxClaims();

        Map<String, Object> result = Map.of(
                "valid", isActive && claimsAvailable,
                "status", isExpired ? "EXPIRED" : entity.getStatus(),
                "verificationMethod", entity.getVerificationMethod(),
                "subjectType", entity.getSubjectType(),
                "expiresAt", entity.getExpiresAt().toString(),
                "claimsRemaining", Math.max(0, entity.getMaxClaims() - entity.getClaimCount())
        );

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
