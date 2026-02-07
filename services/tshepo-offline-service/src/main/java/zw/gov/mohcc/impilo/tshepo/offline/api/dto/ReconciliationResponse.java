package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing reconciliation batch status.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationResponse(
        UUID batchId,
        UUID tenantId,
        UUID facilityId,
        String submittedBy,
        int actionCount,
        int reconciledCount,
        int failedCount,
        String status,
        Instant submittedAt,
        Instant completedAt
) {}
