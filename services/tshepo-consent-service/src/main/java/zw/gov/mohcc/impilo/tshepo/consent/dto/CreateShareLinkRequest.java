package zw.gov.mohcc.impilo.tshepo.consent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a share link.
 */
public record CreateShareLinkRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotBlank(message = "patientRef is required")
        String patientRef,

        @NotBlank(message = "scope is required")
        String scope,

        @NotBlank(message = "purpose is required")
        String purpose,

        @Min(value = 1, message = "maxUses must be at least 1")
        @Max(value = 100, message = "maxUses must not exceed 100")
        int maxUses,

        @Min(value = 1, message = "expiresInHours must be at least 1")
        int expiresInHours
) {
}
