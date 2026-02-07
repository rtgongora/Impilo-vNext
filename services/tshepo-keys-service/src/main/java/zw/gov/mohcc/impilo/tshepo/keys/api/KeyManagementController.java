package zw.gov.mohcc.impilo.tshepo.keys.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.RotateKeyRequest;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.RotateKeyResponse;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.SigningKeyResponse;
import zw.gov.mohcc.impilo.tshepo.keys.core.JwksService;
import zw.gov.mohcc.impilo.tshepo.keys.core.KeyRotationService;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only key management endpoints.
 *
 * <p>Provides CRUD-like operations for signing keys: listing, retrieving,
 * rotating, and revoking keys. All operations are tenant-scoped.</p>
 */
@RestController
@RequestMapping("/v1/keys")
public class KeyManagementController {

    private final SigningKeyRepository signingKeyRepository;
    private final KeyRotationService keyRotationService;
    private final JwksService jwksService;

    public KeyManagementController(SigningKeyRepository signingKeyRepository,
                                    KeyRotationService keyRotationService,
                                    JwksService jwksService) {
        this.signingKeyRepository = signingKeyRepository;
        this.keyRotationService = keyRotationService;
        this.jwksService = jwksService;
    }

    /**
     * POST /v1/keys/rotate — Trigger manual key rotation.
     */
    @PostMapping("/rotate")
    public ResponseEntity<RotateKeyResponse> rotateKey(@Valid @RequestBody RotateKeyRequest request,
                                                        @AuthenticationPrincipal Jwt jwt) {
        String rotatedBy = jwt != null ? jwt.getSubject() : "unknown";

        // Get the current active key ID before rotation
        List<SigningKeyEntity> currentKeys = signingKeyRepository.findActiveKeysByTenant(request.tenantId());
        String oldKeyId = currentKeys.isEmpty() ? null : currentKeys.get(0).getKeyId();

        SigningKeyEntity newKey = keyRotationService.rotateKey(request.tenantId(), rotatedBy, request.reason());

        // Invalidate JWKS cache after rotation
        jwksService.invalidateCache();

        RotateKeyResponse response = new RotateKeyResponse(
                oldKeyId,
                newKey.getKeyId(),
                newKey.getStatus(),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/keys — List all signing keys for a tenant.
     */
    @GetMapping
    public ResponseEntity<List<SigningKeyResponse>> listKeys(@RequestParam UUID tenantId) {
        List<SigningKeyResponse> keys = signingKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(keys);
    }

    /**
     * GET /v1/keys/{keyId} — Get a specific signing key by key ID.
     */
    @GetMapping("/{keyId}")
    public ResponseEntity<SigningKeyResponse> getKey(@PathVariable String keyId) {
        return signingKeyRepository.findByKeyId(keyId)
                .map(entity -> ResponseEntity.ok(toResponse(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /v1/keys/{keyId} — Revoke a specific signing key.
     *
     * <p>This does not delete the key; it marks it as REVOKED so it can no
     * longer be used for signing but can still be used for verification.</p>
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(@PathVariable String keyId,
                                           @AuthenticationPrincipal Jwt jwt) {
        String revokedBy = jwt != null ? jwt.getSubject() : "unknown";
        keyRotationService.revokeKey(keyId, revokedBy);
        jwksService.invalidateCache();
        return ResponseEntity.noContent().build();
    }

    private SigningKeyResponse toResponse(SigningKeyEntity entity) {
        return new SigningKeyResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getKeyId(),
                entity.getAlgorithm(),
                entity.getPublicKeyPem(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getRotatedAt(),
                entity.getExpiresAt()
        );
    }
}
