package zw.gov.mohcc.impilo.tshepo.keys.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.IssueTokenRequest;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.SignPayloadRequest;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.SignPayloadResponse;
import zw.gov.mohcc.impilo.tshepo.keys.core.Ed25519SigningService;
import zw.gov.mohcc.impilo.tshepo.keys.core.TokenSigningService;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Internal signing endpoints for service-to-service use.
 *
 * <p>Used by other Impilo services to sign payloads (referral packages,
 * QR codes, share links) and issue scoped service tokens.</p>
 *
 * <p>These endpoints require JWT authentication but are not restricted
 * to admin roles -- any authenticated service can call them.</p>
 */
@RestController
@RequestMapping("/v1/sign")
public class SigningController {

    private final Ed25519SigningService signingService;
    private final TokenSigningService tokenSigningService;

    public SigningController(Ed25519SigningService signingService,
                              TokenSigningService tokenSigningService) {
        this.signingService = signingService;
        this.tokenSigningService = tokenSigningService;
    }

    /**
     * POST /v1/sign — Sign a payload with the current active key.
     *
     * <p>If {@code jwsCompact} is true, returns a full JWS compact serialization.
     * Otherwise, returns a raw Base64url-encoded Ed25519 signature.</p>
     */
    @PostMapping
    public ResponseEntity<SignPayloadResponse> signPayload(@Valid @RequestBody SignPayloadRequest request) {
        SigningKeyEntity activeKey = signingService.getCurrentActiveKey(request.tenantId());
        String signature;

        if (request.jwsCompact()) {
            signature = signingService.signJws(request.tenantId(), request.payload());
        } else {
            byte[] payloadBytes = request.payload().getBytes(StandardCharsets.UTF_8);
            signature = signingService.signPayload(request.tenantId(), payloadBytes);
        }

        SignPayloadResponse response = new SignPayloadResponse(
                activeKey.getKeyId(),
                activeKey.getAlgorithm(),
                signature
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/sign/token — Issue a scoped, short-lived JWS service token.
     *
     * <p>Returns the token and its expiry. Maps to
     * {@code zw.gov.mohcc.impilo.tshepo.contracts.dto.TokenResponse}.</p>
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> issueToken(@Valid @RequestBody IssueTokenRequest request) {
        String token = tokenSigningService.issueToken(
                request.tenantId(),
                request.actorId(),
                request.actorType(),
                request.purpose(),
                request.targetService(),
                request.scope(),
                request.facilityId(),
                request.subjectHealthId(),
                request.ttlSeconds()
        );

        long expiresAt = tokenSigningService.computeExpiresAt(request.ttlSeconds());

        Map<String, Object> response = Map.of(
                "token", token,
                "expiresAt", expiresAt,
                "scope", request.scope(),
                "targetService", request.targetService()
        );

        return ResponseEntity.ok(response);
    }
}
