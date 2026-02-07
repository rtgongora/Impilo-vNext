package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to create a new Health ID → CPID mapping.
 *
 * <p>If no CPID is supplied, one will be deterministically generated (UUID v5).</p>
 */
public record CreateMappingRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId,
        UUID crid
) {}
