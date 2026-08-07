package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;
import zw.gov.mohcc.impilo.tshepo.identity.events.IdentityEventTypes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Transactional outbox entity for reliable event publishing to Kafka.
 *
 * <p>Events are written to this table in the same transaction as the domain
 * operation. A background poller then publishes unpublished rows to Kafka
 * and marks them as published. This guarantees at-least-once delivery.</p>
 */
@Entity
@Table(name = "event_outbox", schema = "tshepo_identity")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

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

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // Federation context has no safe default at publish time: OutboxEventBuilder refuses to
    // build without it. "national" is the value FederationAuthority.isNational() recognises —
    // "national-spine", used elsewhere in the estate, is not.
    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national";

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    private static final String PRODUCER = "tshepo-identity-service";

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
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
    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override public Long id() { return self.id; }
            @Override public String aggregateType() { return self.aggregateType; }
            @Override public String aggregateId() { return self.aggregateId; }
            @Override public String payloadJson() { return self.payload; }
            @Override public OffsetDateTime occurredAt() { return toOffset(self.occurredAt); }
            @Override public OffsetDateTime publishedAt() { return toOffset(self.publishedAt); }
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

            /**
             * The stored type is legacy (MAPPING_CREATED, TOKEN_ISSUED…) and is not a valid
             * v1.1 event type; CompanionOutboxPublisher puts this straight into the envelope.
             */
            @Override public String eventType() {
                return IdentityEventTypes.canonical(self.aggregateType, self.eventType);
            }
        };
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

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
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    // --- Getters and Setters ---

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
