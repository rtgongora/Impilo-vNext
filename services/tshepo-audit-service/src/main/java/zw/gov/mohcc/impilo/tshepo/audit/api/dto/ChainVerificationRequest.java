package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to verify the hash chain integrity for a tenant.
 */
public record ChainVerificationRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId
) {
}
