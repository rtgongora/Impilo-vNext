package zw.gov.mohcc.impilo.reporting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create a report schedule.
 *
 * @param cronExpression cron expression for scheduling
 * @param parameters     optional runtime parameters as JSON
 * @param outputFormat   optional output format (JSON or CSV)
 */
public record CreateScheduleRequest(
        @NotBlank(message = "cronExpression is required")
        String cronExpression,

        String parameters,

        String outputFormat
) {
}
