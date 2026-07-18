package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to create a new Health ID → CPID mapping.
 *
 * <p>The CPID is minted server-side as an independent random UUID; callers never
 * supply or compute it (Identity Contract §7).</p>
 */
public record CreateMappingRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId,
        UUID crid
) {}
