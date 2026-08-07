package zw.gov.mohcc.impilo.sharedkernel.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder that constructs a v1.1-compliant {@link EventEnvelope}
 * from outbox row data. Handles DeltaPayload wrapping, partition key
 * injection into meta, and event type construction.
 *
 * <p>Usage:
 * <pre>{@code
 * EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
 *     .aggregateType("CLIENT")
 *     .aggregateId("cli-123")
 *     .eventType("impilo.vito.client.created.v1")
 *     .tenantId("tenant-1")
 *     .podId("national")
 *     .correlationId("corr-1")
 *     .idempotencyKey("idem-1")
 *     .causationId("cause-1")
 *     .occurredAt(OffsetDateTime.now())
 *     .deltaCreate(afterState)
 *     .build();
 * }</pre>
 */
public final class OutboxEventBuilder {

    private final String producer;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private int schemaVersion = 1;
    private String tenantId;
    private String podId;
    private String correlationId;
    private String causationId;
    private String idempotencyKey;
    private OffsetDateTime occurredAt;
    private String subjectType;
    private String subjectId;
    private String partitionKey;
    private Map<String, Object> payload;
    private Map<String, Object> extraMeta;

    private OutboxEventBuilder(String producer) {
        this.producer = Objects.requireNonNull(producer, "producer is required");
    }

    /**
     * Start building an envelope for a specific producer service.
     */
    public static OutboxEventBuilder forProducer(String producer) {
        return new OutboxEventBuilder(producer);
    }

    public OutboxEventBuilder aggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    public OutboxEventBuilder aggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    public OutboxEventBuilder eventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    public OutboxEventBuilder schemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }

    public OutboxEventBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public OutboxEventBuilder podId(String podId) {
        this.podId = podId;
        return this;
    }

    public OutboxEventBuilder correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public OutboxEventBuilder causationId(String causationId) {
        this.causationId = causationId;
        return this;
    }

    public OutboxEventBuilder idempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public OutboxEventBuilder occurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }

    public OutboxEventBuilder subjectType(String subjectType) {
        this.subjectType = subjectType;
        return this;
    }

    public OutboxEventBuilder subjectId(String subjectId) {
        this.subjectId = subjectId;
        return this;
    }

    /**
     * Set a custom partition key. Defaults to subjectId if not set.
     */
    public OutboxEventBuilder partitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
        return this;
    }

    /**
     * Set the payload directly (for events that don't use DeltaPayload).
     */
    public OutboxEventBuilder payload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    /**
     * Set extra metadata fields beyond partition_key.
     */
    public OutboxEventBuilder extraMeta(Map<String, Object> extraMeta) {
        this.extraMeta = extraMeta;
        return this;
    }

    /**
     * Build a DeltaPayload for a CREATE operation and set it as the envelope payload.
     * @param after the state after creation
     */
    public OutboxEventBuilder deltaCreate(Map<String, Object> after) {
        DeltaPayload delta = new DeltaPayload("CREATE", null, after,
                after != null ? List.copyOf(after.keySet()) : List.of());
        this.payload = deltaToMap(delta);
        return this;
    }

    /**
     * Build a DeltaPayload for an UPDATE operation.
     * @param before state before the change
     * @param after state after the change
     * @param changedFields list of field paths that changed
     */
    public OutboxEventBuilder deltaUpdate(Map<String, Object> before,
                                           Map<String, Object> after,
                                           List<String> changedFields) {
        DeltaPayload delta = new DeltaPayload("UPDATE", before, after, changedFields);
        this.payload = deltaToMap(delta);
        return this;
    }

    /**
     * Build a DeltaPayload for a DELETE operation.
     * @param before the state before deletion
     */
    public OutboxEventBuilder deltaDelete(Map<String, Object> before) {
        DeltaPayload delta = new DeltaPayload("DELETE", before, null,
                before != null ? List.copyOf(before.keySet()) : List.of());
        this.payload = deltaToMap(delta);
        return this;
    }

    /**
     * Build a DeltaPayload for a custom operation (MERGE, REVOKE, etc.).
     */
    public OutboxEventBuilder delta(String op, Map<String, Object> before,
                                     Map<String, Object> after,
                                     List<String> changedFields) {
        DeltaPayload delta = new DeltaPayload(op, before, after, changedFields);
        this.payload = deltaToMap(delta);
        return this;
    }

    /**
     * Build the EventEnvelope. Auto-generates eventId, emittedAt, and defaults for
     * optional fields.
     */
    public EventEnvelope build() {
        requireFederationContext("tenantId", tenantId);
        requireFederationContext("podId", podId);

        String resolvedSubjectType = subjectType != null ? subjectType : aggregateType;
        String resolvedSubjectId = subjectId != null ? subjectId : aggregateId;
        String resolvedPartitionKey = partitionKey != null ? partitionKey : resolvedSubjectId;

        Map<String, Object> meta = new HashMap<>();
        meta.put("partition_key", resolvedPartitionKey);
        if (extraMeta != null) {
            meta.putAll(extraMeta);
        }

        return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .schemaVersion(schemaVersion)
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .causationId(causationId != null ? causationId : UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString())
                .producer(producer)
                .tenantId(tenantId)
                .podId(podId)
                .occurredAt(occurredAt != null ? occurredAt : OffsetDateTime.now())
                .subjectType(resolvedSubjectType)
                .subjectId(resolvedSubjectId)
                .payload(payload != null ? payload : Map.of())
                .meta(Collections.unmodifiableMap(meta))
                .build();
    }

    /**
     * Federation context is not optional and has no safe default.
     *
     * <p>This used to fall back to the literal {@code "unknown"}. That is worse than an
     * error: an event published with {@code tenant_id="unknown"} is indistinguishable on
     * the wire from one whose tenant genuinely is unknown, and every downstream consumer,
     * routing rule and audit record then treats the invented value as fact. Refusing to
     * build is recoverable; a fabricated tenant on a national topic is not.</p>
     *
     * @throws IllegalStateException if the value is missing or blank
     */
    private void requireFederationContext(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Cannot build EventEnvelope: " + field + " is required and has no default. "
                            + "Populate it on the outbox row rather than publishing a placeholder "
                            + "(producer=" + producer + ", eventType=" + eventType + ")");
        }
    }

    private static Map<String, Object> deltaToMap(DeltaPayload delta) {
        Map<String, Object> map = new HashMap<>();
        map.put("op", delta.op());
        if (delta.before() != null) {
            map.put("before", delta.before());
        }
        if (delta.after() != null) {
            map.put("after", delta.after());
        }
        map.put("changed_fields", delta.changedFields());
        return map;
    }
}
