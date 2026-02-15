package zw.gov.mohcc.impilo.pipeline.api.dto;

/**
 * Response returned after successfully ingesting an event.
 */
public record IngestResponse(
        String eventId,
        String status,
        String message
) {
}
