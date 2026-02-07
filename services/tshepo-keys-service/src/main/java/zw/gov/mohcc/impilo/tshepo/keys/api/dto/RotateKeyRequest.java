package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to trigger a manual key rotation for a specific tenant.
 */
public record RotateKeyRequest(
        @NotNull UUID tenantId,
        String reason
) {}
