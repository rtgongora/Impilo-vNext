package zw.gov.mohcc.impilo.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a new report definition.
 *
 * @param reportKey      unique human-readable key (lowercase, hyphens, underscores)
 * @param name           display name
 * @param description    optional description
 * @param queryTemplate  query template (stub SQL or NDR query)
 * @param parameters     optional JSON parameter schema
 * @param outputFormat   JSON or CSV (defaults to JSON)
 */
public record CreateReportRequest(
        @NotBlank(message = "reportKey is required")
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "reportKey must be lowercase alphanumeric with hyphens/underscores")
        String reportKey,

        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotBlank(message = "queryTemplate is required")
        String queryTemplate,

        String parameters,

        String outputFormat
) {
}
