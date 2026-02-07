package zw.gov.mohcc.impilo.tshepo.consent.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkCreatedResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkValidationResult;
import zw.gov.mohcc.impilo.tshepo.consent.service.ShareLinkService;

import java.util.UUID;

/**
 * REST controller for share link governance.
 *
 * <p>Share links are short-lived access tokens that allow patients to delegate
 * temporary data access to third parties. Tokens are SHA-256 hashed before storage.</p>
 */
@RestController
@RequestMapping("/v1/consent/share-links")
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    /**
     * Creates a new share link. The plaintext token is returned exactly once.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShareLinkCreatedResponse>> create(
            @Valid @RequestBody CreateShareLinkRequest request) {
        String actorId = resolveActorId();
        String correlationId = resolveCorrelationId();
        ShareLinkCreatedResponse response = shareLinkService.create(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, correlationId));
    }

    /**
     * Validates and consumes a share link token.
     * Returns the share link details if valid, or an error if expired/revoked/exhausted.
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<ShareLinkValidationResult>> validate(
            @PathVariable String token) {
        String correlationId = resolveCorrelationId();
        ShareLinkValidationResult result = shareLinkService.validateAndConsume(token);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Revokes a share link by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID id) {
        String actorId = resolveActorId();
        String correlationId = resolveCorrelationId();
        shareLinkService.revoke(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok(null, correlationId));
    }

    // --- Helpers ---

    private String resolveActorId() {
        var ctx = TrustContextHolder.get();
        if (ctx != null && ctx.actorId() != null) {
            return ctx.actorId();
        }
        return "unknown";
    }

    private String resolveCorrelationId() {
        var ctx = TrustContextHolder.get();
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
