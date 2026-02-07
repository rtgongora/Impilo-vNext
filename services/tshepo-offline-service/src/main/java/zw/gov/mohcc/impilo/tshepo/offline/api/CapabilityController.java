package zw.gov.mohcc.impilo.tshepo.offline.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenResponse;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyResponse;
import zw.gov.mohcc.impilo.tshepo.offline.core.CapabilityTokenService;

import java.util.UUID;

/**
 * REST controller for offline capability token operations.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *     <li>POST /v1/offline/capabilities — issue a new offline capability token</li>
 *     <li>GET /v1/offline/capabilities/{id} — retrieve a capability token</li>
 *     <li>DELETE /v1/offline/capabilities/{id} — revoke a capability token</li>
 *     <li>POST /v1/offline/capabilities/verify — verify a signed capability token</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/offline/capabilities")
public class CapabilityController {

    private static final Logger log = LoggerFactory.getLogger(CapabilityController.class);

    private final CapabilityTokenService tokenService;

    public CapabilityController(CapabilityTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Issue a new offline capability token.
     * Validates actor facility access, determines capabilities, signs with Ed25519.
     */
    @PostMapping
    public ResponseEntity<CapabilityTokenResponse> issueToken(
            @Valid @RequestBody CapabilityTokenRequest request,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @RequestHeader(TrustHeaders.CORRELATION_ID) UUID correlationId) {

        log.info("POST /v1/offline/capabilities — tenant={}, actor={}, correlation={}",
                tenantId, actorId, correlationId);

        CapabilityTokenResponse response = tokenService.issueToken(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve a capability token by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CapabilityTokenResponse> getToken(
            @PathVariable UUID id,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId) {

        log.debug("GET /v1/offline/capabilities/{} — tenant={}", id, tenantId);

        CapabilityTokenResponse response = tokenService.getToken(id, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke an active capability token.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeToken(
            @PathVariable UUID id,
            @RequestHeader(TrustHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(TrustHeaders.ACTOR_ID) String actorId,
            @RequestHeader(TrustHeaders.CORRELATION_ID) UUID correlationId) {

        log.info("DELETE /v1/offline/capabilities/{} — tenant={}, actor={}", id, tenantId, actorId);

        tokenService.revokeToken(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Verify a signed capability token.
     * Checks JWS signature, expiry, and revocation status.
     */
    @PostMapping("/verify")
    public ResponseEntity<CapabilityVerifyResponse> verifyToken(
            @Valid @RequestBody CapabilityVerifyRequest request) {

        log.debug("POST /v1/offline/capabilities/verify");

        CapabilityVerifyResponse response = tokenService.verifyToken(request);
        return ResponseEntity.ok(response);
    }
}
