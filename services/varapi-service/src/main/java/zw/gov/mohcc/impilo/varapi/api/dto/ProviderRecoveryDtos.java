package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs for the provider profile <b>recovery</b> lane (IATG WS-F).
 *
 * <p>Recovery is <b>recover-not-reissue</b>: a person who already owns a
 * provider profile re-establishes their login binding to the <i>same</i>
 * {@code providerPublicId}. No new provider record is ever minted on this
 * path — the recovery token is bound to the existing profile.</p>
 */
public final class ProviderRecoveryDtos {

    private ProviderRecoveryDtos() {
    }

    /**
     * Evidence supporting a recovery initiation. Mirrors the trust-evidence
     * shape used across the provider trust lane ({@code type}, {@code source},
     * {@code ref}, {@code confidence}); {@code ref} should arrive pre-masked
     * from the experience layer.
     */
    public record RecoveryEvidence(
            String type,
            String source,
            String ref,
            Double confidence
    ) {
    }

    /** POST /v1/internal/providers/recovery/initiate request. */
    public record RecoveryInitiateRequest(
            UUID healthId,
            RecoveryEvidence evidence
    ) {
    }

    /**
     * Recovery initiation result. The raw {@code recoveryToken} is returned
     * exactly once here; only its SHA-256 hash is persisted.
     */
    public record RecoveryInitiateResponse(
            String providerPublicId,
            String recoveryToken,
            Instant expiresAt
    ) {
    }

    /** POST /v1/internal/providers/recovery/complete request. */
    public record RecoveryCompleteRequest(
            String token
    ) {
    }

    /**
     * Recovery completion result. {@code providerPublicId} is guaranteed to be
     * the SAME identifier the profile had before recovery (recover-not-reissue).
     */
    public record RecoveryCompleteResponse(
            String providerPublicId,
            UUID impiloHealthId,
            String lifecycleStatus
    ) {
    }
}
