package zw.gov.mohcc.impilo.reporting.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a report run.
 */
public record ReportRunResponse(
        UUID runId,
        String reportKey,
        String parameters,
        String outputFormat,
        String status,
        String result,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String createdBy,
        OffsetDateTime createdAt
) {
}
