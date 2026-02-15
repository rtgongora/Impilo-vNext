package zw.gov.mohcc.impilo.pipeline.api.dto;

import java.time.OffsetDateTime;

/**
 * Watermark state for a given source.
 */
public record WatermarkResponse(
        String sourceId,
        String tenantId,
        String lastEventId,
        OffsetDateTime lastOccurredAt,
        long eventCount,
        OffsetDateTime updatedAt
) {
}
