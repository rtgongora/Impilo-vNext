package zw.gov.mohcc.impilo.vito.events;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * V1.1 event envelope per Manifest v1.1 Companion Spec.
 *
 * All fields validated at construction time. Mandatory fields:
 *   event_id, event_type, schema_version (>=1), correlation_id, causation_id,
 *   idempotency_key, producer, tenant_id, pod_id, occurred_at, emitted_at,
 *   subject_type, subject_id, payload
 *
 * Convention:
 *   event_type = "impilo.vito.<aggregate>.<event>.v<version>"
 *   producer   = "vito"
 *   meta.partition_key = aggregate_id (for Kafka partitioning)
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        String correlationId,
        String causationId,
        String idempotencyKey,
        String producer,
        String tenantId,
        String podId,
        OffsetDateTime occurredAt,
        OffsetDateTime emittedAt,
        String subjectType,
        String subjectId,
        Map<String, Object> payload,
        Map<String, Object> meta
) {
    public EventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event_id is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("event_type is required");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException(
                    eventType + ": schema_version missing/invalid — value was " + schemaVersion);
        }
        if (correlationId == null) throw new IllegalArgumentException("correlation_id is required");
        if (causationId == null) throw new IllegalArgumentException("causation_id is required");
        if (idempotencyKey == null) throw new IllegalArgumentException("idempotency_key is required");
        if (producer == null) throw new IllegalArgumentException("producer is required");
        if (tenantId == null) throw new IllegalArgumentException("tenant_id is required");
        if (podId == null) throw new IllegalArgumentException("pod_id is required");
        if (occurredAt == null) throw new IllegalArgumentException("occurred_at is required");
        if (emittedAt == null) throw new IllegalArgumentException("emitted_at is required");
        if (subjectType == null) throw new IllegalArgumentException("subject_type is required");
        if (subjectId == null) throw new IllegalArgumentException("subject_id is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
    }

    /**
     * Convenience: the partition key for Kafka (from meta or subjectId).
     */
    public String partitionKey() {
        if (meta != null && meta.containsKey("partition_key")) {
            return String.valueOf(meta.get("partition_key"));
        }
        return subjectId;
    }
}
