package zw.gov.mohcc.impilo.sharedkernel.events;

import zw.gov.mohcc.impilo.sharedkernel.schema.SchemaValidationException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical v1.1 event envelope. Every domain event emitted by any Impilo service
 * must be wrapped in this envelope before publishing to Kafka.
 * <p>
 * Constructed exclusively via {@link Builder} to make it hard to create an invalid envelope.
 *
 * @param eventId        unique event identifier (ULID or UUID)
 * @param eventType      dot-notation event type (e.g. "vito.patient.created")
 * @param schemaVersion  version of the payload schema (must be >= 1)
 * @param correlationId  end-to-end correlation identifier
 * @param causationId    identifier of the event that caused this event
 * @param idempotencyKey client-supplied idempotency key for command deduplication
 * @param producer       name of the producing service (e.g. "vito-service")
 * @param tenantId       tenant scope
 * @param podId          originating pod/facility identifier
 * @param occurredAt     when the domain event actually happened
 * @param emittedAt      when the envelope was created (clock skew tracking)
 * @param subjectType    type of the subject (e.g. "Patient", "Order")
 * @param subjectId      identifier of the subject
 * @param payload        the event payload (domain-specific)
 * @param meta           optional metadata (tracing, version hints, etc.)
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

    /**
     * Compact constructor — validates all required fields.
     */
    public EventEnvelope {
        Objects.requireNonNull(eventId, "event_id is required");
        Objects.requireNonNull(eventType, "event_type is required");
        if (schemaVersion < 1) {
            throw new SchemaValidationException(
                    eventType + ": schema_version missing/invalid — value was " + schemaVersion);
        }
        Objects.requireNonNull(correlationId, "correlation_id is required");
        Objects.requireNonNull(causationId, "causation_id is required");
        Objects.requireNonNull(idempotencyKey, "idempotency_key is required");
        Objects.requireNonNull(producer, "producer is required");
        Objects.requireNonNull(tenantId, "tenant_id is required");
        Objects.requireNonNull(podId, "pod_id is required");
        Objects.requireNonNull(occurredAt, "occurred_at is required");
        Objects.requireNonNull(emittedAt, "emitted_at is required");
        Objects.requireNonNull(subjectType, "subject_type is required");
        Objects.requireNonNull(subjectId, "subject_id is required");
        Objects.requireNonNull(payload, "payload is required");
        meta = meta != null ? Map.copyOf(meta) : Map.of();
        payload = Map.copyOf(payload);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId;
        private String eventType;
        private int schemaVersion;
        private String correlationId;
        private String causationId;
        private String idempotencyKey;
        private String producer;
        private String tenantId;
        private String podId;
        private OffsetDateTime occurredAt;
        private OffsetDateTime emittedAt;
        private String subjectType;
        private String subjectId;
        private Map<String, Object> payload;
        private Map<String, Object> meta;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder podId(String podId) {
            this.podId = podId;
            return this;
        }

        public Builder occurredAt(OffsetDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder emittedAt(OffsetDateTime emittedAt) {
            this.emittedAt = emittedAt;
            return this;
        }

        public Builder subjectType(String subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder subjectId(String subjectId) {
            this.subjectId = subjectId;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        /**
         * Auto-generates eventId (UUID) and emittedAt (now) if not set.
         */
        public EventEnvelope build() {
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (emittedAt == null) {
                emittedAt = OffsetDateTime.now();
            }
            return new EventEnvelope(
                    eventId, eventType, schemaVersion,
                    correlationId, causationId, idempotencyKey,
                    producer, tenantId, podId,
                    occurredAt, emittedAt,
                    subjectType, subjectId,
                    payload, meta
            );
        }
    }
}
