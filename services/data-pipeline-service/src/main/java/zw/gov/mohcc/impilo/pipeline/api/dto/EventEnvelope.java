package zw.gov.mohcc.impilo.pipeline.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * v1.1 EventEnvelope — the canonical event format for the data pipeline.
 *
 * <p>Mirrors the Manifest v1.1 event contract. All fields except optional
 * ones (causationId, schemaVersion) are required for ingestion.</p>
 */
public record EventEnvelope(
        String eventId,
        String tenantId,
        String sourceId,
        String eventType,
        String aggregateType,
        String aggregateId,
        OffsetDateTime occurredAt,
        String podId,
        String correlationId,
        String causationId,
        Map<String, Object> payload,
        String schemaVersion
) {
}
