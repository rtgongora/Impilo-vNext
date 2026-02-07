package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to issue a scoped, short-lived JWS token.
 *
 * <p>Used by identity-service and offline-service for service-to-service trust.
 * Maps closely to {@code zw.gov.mohcc.impilo.tshepo.contracts.dto.TokenRequest}.</p>
 */
public record IssueTokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String actorType,
        @NotBlank String purpose,
        @NotBlank String targetService,
        @NotBlank String scope,
        UUID facilityId,
        UUID subjectHealthId,
        /** TTL in seconds. If zero or negative, uses the configured default. */
        int ttlSeconds
) {}
