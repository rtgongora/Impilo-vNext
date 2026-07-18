package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to issue (find-or-create) the mapped CPID for a tenant ID and Health ID.
 */
public record GenerateCpidRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId
) {}
