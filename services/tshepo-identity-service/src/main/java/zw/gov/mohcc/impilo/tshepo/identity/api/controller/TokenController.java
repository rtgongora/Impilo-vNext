package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.core.TokenIssuanceService;

/**
 * Scoped token issuance, introspection, and revocation endpoints.
 *
 * <p>Tokens are short-lived JWS (Ed25519-signed) tokens for internal
 * service-to-service authorization. They are NOT OIDC tokens.</p>
 */
@RestController
@RequestMapping("/v1/identity/tokens")
public class TokenController {

    private final TokenIssuanceService tokenService;

    public TokenController(TokenIssuanceService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Issue a scoped access token for a downstream service.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ScopedTokenResponse>> issueToken(
            @Valid @RequestBody IssueScopedTokenRequest request) {
        ScopedTokenResponse result = tokenService.issueToken(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Introspect a scoped token: verify it is active, not expired, and not revoked.
     */
    @PostMapping("/introspect")
    public ResponseEntity<ApiResponse<IntrospectResponse>> introspect(
            @Valid @RequestBody IntrospectRequest request) {
        IntrospectResponse result = tokenService.introspect(request);
        return ResponseEntity.ok(ApiResponse.ok(result, null));
    }

    /**
     * Revoke a scoped token by its JTI (JWT ID).
     */
    @DeleteMapping("/{jti}")
    public ResponseEntity<ApiResponse<Void>> revokeToken(@PathVariable String jti) {
        tokenService.revokeToken(jti);
        return ResponseEntity.ok(ApiResponse.ok(null, null));
    }
}
