package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Request DTO to author a preliminary or final diagnostic report.
 *
 * <p>{@code summary} and {@code docIds} are free-form JSON (serialized by the controller);
 * {@code impression} and {@code recommendations} are structured narrative sections.</p>
 */
public record ReportRequest(
        Object summary,
        String impression,
        String recommendations,
        Object docIds,
        String ziboResultCodes
) {}
