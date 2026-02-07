package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue a scoped, short-lived access token for downstream services.
 */
public record TokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String actorType,
        @NotBlank String purpose,
        @NotBlank String targetService,
        @NotBlank String scope,
        UUID facilityId,
        UUID subjectHealthId,
        int ttlSeconds
) {}
