package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to sign an arbitrary payload with the tenant's active Ed25519 key.
 *
 * <p>Used for referral package signing, QR code JWS, share-link signatures, and
 * purpose-scoped trust artefacts (offline capability tokens, offline packs).</p>
 */
public record SignPayloadRequest(
        @NotNull UUID tenantId,
        @NotBlank String payload,
        /** If true, produce a JWS compact serialization; otherwise raw Ed25519 signature. */
        boolean jwsCompact,
        /**
         * Optional {@link zw.gov.mohcc.impilo.tshepo.keys.core.KeyPurpose} name. When set,
         * the tenant's active key for that purpose is used (fail-closed if none is
         * provisioned for sensitive purposes). Null/blank ⇒ GENERAL.
         */
        String purpose
) {}
