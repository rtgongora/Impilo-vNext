package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for the {@code tshepo_consent.event_outbox} table.
 *
 * <p>Implements the transactional outbox pattern for reliable Kafka event delivery.
 * Events are written to this table within the same transaction as the consent mutation,
 * then asynchronously polled and published to Kafka.</p>
 *
 * <p>{@code payloadJson} holds the domain payload alone, never a nested envelope — the
 * envelope is built at publish time, which is what lets the same row feed both the legacy
 * topic (raw) and the v1.1 topic (enveloped).</p>
 */
@Entity
@Table(name = "event_outbox", schema = "tshepo_consent")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
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

    // Federation context has no safe default at publish time: OutboxEventBuilder refuses
    // to build without it. "national" is the value FederationAuthority.isNational()
    // recognises — "national-spine", used elsewhere in the estate, is not.
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

    private static final String PRODUCER = "tshepo-consent-service";

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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
             * The stored event type is legacy (CONSENT_GRANTED, SHARE_LINK_REVOKED…) and is
             * not a valid v1.1 event type. Passing it through unchanged would emit an
             * envelope that fails the event-type contract, so the canonical form is derived
             * from the aggregate and the action already present in the stored value.
             */
            @Override public String eventType() {
                return zw.gov.mohcc.impilo.tshepo.consent.events.ConsentEventTypes
                        .canonical(self.aggregateType, self.eventType);
            }
        };
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
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
}
