package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue a scoped, short-lived JWS access token for a downstream service.
 */
public record IssueScopedTokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String purpose,
        @NotBlank String targetService,
        @NotBlank String scope,
        String subjectRef,
        Integer ttlSeconds
) {}
