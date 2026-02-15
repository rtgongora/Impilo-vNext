package zw.gov.mohcc.impilo.reporting.dto;

/**
 * Request to run a report.
 *
 * @param parameters   optional runtime parameters as JSON
 * @param outputFormat optional output format override (JSON or CSV)
 */
public record RunReportRequest(
        String parameters,
        String outputFormat
) {
}
