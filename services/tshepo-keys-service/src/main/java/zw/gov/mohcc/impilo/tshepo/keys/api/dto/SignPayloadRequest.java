package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to sign an arbitrary payload with the tenant's current active Ed25519 key.
 *
 * <p>Used for referral package signing, QR code JWS, and share-link signatures.</p>
 */
public record SignPayloadRequest(
        @NotNull UUID tenantId,
        @NotBlank String payload,
        /** If true, produce a JWS compact serialization; otherwise raw Ed25519 signature. */
        boolean jwsCompact
) {}
