package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request to create a signed audit export bundle.
 */
public record AuditExportRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotNull(message = "fromSequence is required")
        @Positive
        Long fromSequence,

        @NotNull(message = "toSequence is required")
        @Positive
        Long toSequence,

        @NotNull(message = "requestedBy is required")
        String requestedBy
) {
}
