package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to approve or reject a break-glass request.
 */
public record BreakGlassReviewRequest(
        @NotNull UUID tenantId,
        @NotBlank String reviewerId,
        boolean approved
) {}
