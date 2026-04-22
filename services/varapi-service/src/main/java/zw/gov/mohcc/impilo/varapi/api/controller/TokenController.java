package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.RecoveryStartRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.RecoveryVerifyRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.TokenIssueResponse;
import zw.gov.mohcc.impilo.varapi.core.TokenService;

/**
 * Internal REST API for provider token lifecycle management.
 *
 * Manages the issuance, rotation, and recovery of provider authentication tokens.
 * Tokens are issued as one-time plaintext reveals; after issuance only the
 * Argon2 verifier is stored.
 *
 * CRITICAL: All responses MUST be GENERIC to prevent enumeration.
 * Never reveal whether a provider exists or not through response shape,
 * timing, or error messages. Always return the same structure.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/provider-token")
public class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> issueToken(
            @RequestParam String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing provider token correlationId={}", ctx.correlationId());

        // GENERIC response — same shape/timing whether provider exists or not
        TokenService.TokenIssuanceResult result = tokenService.issueToken(providerPublicId);
        TokenIssueResponse response = toTokenIssueResponse(result);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/rotate")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> rotateToken(
            @RequestParam String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Rotating provider token correlationId={}", ctx.correlationId());

        // GENERIC response — same shape/timing whether provider exists or not
        TokenService.TokenIssuanceResult result = tokenService.rotateToken(providerPublicId);
        TokenIssueResponse response = toTokenIssueResponse(result);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/recovery/start")
    public ResponseEntity<ApiResponse<GenericResponse>> startRecovery(
            @Valid @RequestBody RecoveryStartRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Starting token recovery correlationId={}", ctx.correlationId());

        // GENERIC response — same shape/timing whether provider exists or not
        tokenService.startRecovery(request.providerPublicId());
        GenericResponse response = GenericResponse.success(
                "If the provider exists, a recovery challenge has been initiated");

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/recovery/verify")
    public ResponseEntity<ApiResponse<TokenIssueResponse>> verifyRecovery(
            @Valid @RequestBody RecoveryVerifyRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Verifying token recovery correlationId={}", ctx.correlationId());

        // GENERIC response — same shape/timing whether provider exists or not
        TokenService.TokenIssuanceResult result = tokenService.verifyRecovery(
                request.providerPublicId(), request.biometricRef());
        TokenIssueResponse response = result != null ? toTokenIssueResponse(result)
                : new TokenIssueResponse(null, null, null, "Recovery failed");

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    private TokenIssueResponse toTokenIssueResponse(TokenService.TokenIssuanceResult result) {
        return new TokenIssueResponse(
                result.providerPublicId(),
                result.plainTextToken(),
                java.time.Instant.now(),
                "Token issued successfully"
        );
    }
}
