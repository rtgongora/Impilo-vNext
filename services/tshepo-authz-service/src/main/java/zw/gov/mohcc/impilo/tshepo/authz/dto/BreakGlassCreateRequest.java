package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to create a break-glass emergency access override.
 */
public record BreakGlassCreateRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String reason,
        String resourceType,
        String resourceId
) {}
