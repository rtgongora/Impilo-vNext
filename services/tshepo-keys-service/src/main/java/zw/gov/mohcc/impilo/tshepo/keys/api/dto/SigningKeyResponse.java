package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing representation of a signing key (never includes private key material).
 */
public record SigningKeyResponse(
        Long id,
        UUID tenantId,
        String keyId,
        String algorithm,
        String publicKeyPem,
        String status,
        Instant createdAt,
        Instant rotatedAt,
        Instant expiresAt
) {}
