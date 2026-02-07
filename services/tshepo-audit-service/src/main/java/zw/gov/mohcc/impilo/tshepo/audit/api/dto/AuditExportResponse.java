package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing export bundle metadata.
 */
public record AuditExportResponse(
        UUID id,
        UUID tenantId,
        String requestedBy,
        Long fromSequence,
        Long toSequence,
        Integer entryCount,
        String bundleHash,
        String signature,
        String status,
        Instant createdAt
) {
}
