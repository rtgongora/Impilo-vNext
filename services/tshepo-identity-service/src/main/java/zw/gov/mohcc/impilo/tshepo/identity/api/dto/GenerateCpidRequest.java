package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate a deterministic CPID from a tenant ID and Health ID.
 */
public record GenerateCpidRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId
) {}
