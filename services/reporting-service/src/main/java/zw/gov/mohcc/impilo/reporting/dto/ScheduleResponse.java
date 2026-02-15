package zw.gov.mohcc.impilo.reporting.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a report schedule.
 */
public record ScheduleResponse(
        UUID scheduleId,
        String reportKey,
        String cronExpression,
        String parameters,
        String outputFormat,
        String status,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastRunAt,
        String createdBy,
        OffsetDateTime createdAt
) {
}
