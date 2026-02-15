package zw.gov.mohcc.impilo.pipeline.api.dto;

/**
 * Request to register a new ingestion source.
 */
public record CreateSourceRequest(
        String sourceId,
        String displayName,
        String description,
        String sourceType
) {
}
