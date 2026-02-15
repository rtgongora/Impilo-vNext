package zw.gov.mohcc.impilo.reporting.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a report definition.
 */
public record ReportDefinitionResponse(
        UUID definitionId,
        String reportKey,
        String name,
        String description,
        String queryTemplate,
        String parameters,
        String outputFormat,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
