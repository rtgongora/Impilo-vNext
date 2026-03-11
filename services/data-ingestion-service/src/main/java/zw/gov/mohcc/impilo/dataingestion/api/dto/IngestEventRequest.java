package zw.gov.mohcc.impilo.dataingestion.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Inbound EventEnvelope JSON as received from producers.
 * Mirrors the shared-kernel EventEnvelope wire format but as a mutable DTO
 * for deserialization (the shared-kernel record has strict validation we
 * apply ourselves with better error messages).
 */
public record IngestEventRequest(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("schema_version") Integer schemaVersion,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("causation_id") String causationId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        String producer,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("pod_id") String podId,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("emitted_at") String emittedAt,
        @JsonProperty("subject_type") String subjectType,
        @JsonProperty("subject_id") String subjectId,
        Map<String, Object> payload,
        Map<String, Object> meta
) {}
