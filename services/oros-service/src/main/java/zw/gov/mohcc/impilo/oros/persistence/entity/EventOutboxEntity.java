package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.oros.events.OrosEventTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox entity for reliable Kafka event publishing.
 *
 * <p>{@code payloadJson} holds the domain payload alone, never a nested envelope — the
 * envelope is built at publish time, which is what lets one row feed both the legacy topics
 * (raw) and the v1.1 topic (enveloped).</p>
 *
 * <p>The v1.1 companion columns default in {@link #onCreate()} rather than at each of the
 * eighteen call sites that construct this entity. All of them already set the tenant, which
 * is the one field that cannot be defaulted truthfully.</p>
 */
@Entity
@Table(name = "oros_event_outbox")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ── v1.1 companion columns ──

    @Column(name = "event_id", nullable = false)
    private UUID eventId = UUID.randomUUID();

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "causation_id", length = 64)
    private String causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = PRODUCER;

    // Federation context has no safe default at publish time: OutboxEventBuilder refuses to
    // build without it. "national" is the value FederationAuthority.isNational() recognises —
    // "national-spine", used elsewhere in the estate, is not.
    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national";

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    private static final String PRODUCER = "oros-service";

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
        if (subjectId == null) {
            subjectId = aggregateId;
        }
        if (subjectType == null) {
            subjectType = aggregateType;
        }
        if (idempotencyKey == null) {
            idempotencyKey = PRODUCER + ":" + aggregateType + ":" + aggregateId + ":"
                    + eventType + ":" + eventId;
        }
    }

    /** Adapt this row to the publisher contract. */
    public OrosOutboxRow toOutboxRow() {
        return new OrosOutboxRow(this);
    }

    /**
     * Exposes both event-type forms. The stored value is legacy ({@code RESULT_CRITICAL},
     * {@code ORDER_PLACED}) and is what the legacy topic routing keys off; the v1.1 envelope
     * needs {@code impilo.{service}.{entity}.{action}.v{N}}, and CompanionOutboxPublisher puts
     * {@code eventType()} straight into the envelope.
     */
    public static final class OrosOutboxRow implements CompanionOutboxPublisher.OutboxRow {

        private final EventOutboxEntity self;

        OrosOutboxRow(EventOutboxEntity self) {
            this.self = self;
        }

        /** The stored event type, which legacy topic routing switches on. */
        public String legacyEventType() {
            return self.eventType;
        }

        @Override public Long id() { return self.id; }
        @Override public String aggregateType() { return self.aggregateType; }
        @Override public String aggregateId() { return self.aggregateId; }
        @Override public String payloadJson() { return self.payload; }
        @Override public OffsetDateTime occurredAt() { return self.occurredAt; }
        @Override public OffsetDateTime publishedAt() { return self.publishedAt; }
        @Override public String tenantId() {
            return self.tenantId != null ? self.tenantId.toString() : null;
        }
        @Override public String podId() { return self.podId; }
        @Override public String correlationId() { return self.correlationId; }
        @Override public String causationId() { return self.causationId; }
        @Override public String idempotencyKey() { return self.idempotencyKey; }
        @Override public int schemaVersion() { return self.schemaVersion; }
        @Override public String subjectType() { return self.subjectType; }
        @Override public String subjectId() { return self.subjectId; }
        @Override public String partitionKey() { return self.aggregateId; }
        @Override public int retryCount() { return self.retryCount; }

        @Override public String eventType() {
            return OrosEventTypes.canonical(self.aggregateType, self.eventType);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getCausationId() { return causationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getProducer() { return producer; }
    public void setProducer(String producer) { this.producer = producer; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
