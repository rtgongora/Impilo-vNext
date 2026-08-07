package zw.gov.mohcc.impilo.referral.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.referral.events.ReferralOutboxRow;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A referral outbox row in the companion v1.1 shape.
 *
 * <p>{@code payloadJson} holds the domain payload alone — never a nested envelope. The
 * envelope is built at publish time by {@code CompanionOutboxPublisher}, which is what lets
 * the same row feed both the legacy topic (raw payload) and the v1.1 topic (enveloped).</p>
 */
@Entity
@Table(name = "event_outbox", schema = "referral")
public class EventOutboxEntity {

    /** The pod that owns this row. Must match FederationAuthority.NATIONAL_POD_ID. */
    public static final String NATIONAL_POD_ID = "national";

    private static final String PRODUCER = "referral-service";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId = UUID.randomUUID();

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

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

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = NATIONAL_POD_ID;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public EventOutboxEntity() {
    }

    /**
     * Create an outbox row. {@code tenantId} is required rather than defaulted — a referral
     * event with an assumed tenant is not federation truth, and the envelope contract now
     * refuses one instead of publishing a placeholder.
     */
    public static EventOutboxEntity create(String aggregateType,
                                           String aggregateId,
                                           String eventType,
                                           UUID tenantId,
                                           String subjectType,
                                           String subjectId,
                                           String payloadJson) {
        EventOutboxEntity e = new EventOutboxEntity();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.tenantId = tenantId;
        e.subjectType = subjectType;
        e.subjectId = subjectId;
        e.payloadJson = payloadJson;
        e.occurredAt = OffsetDateTime.now();
        e.idempotencyKey = PRODUCER + ":" + aggregateType + ":" + aggregateId + ":"
                + eventType + ":" + e.eventId;
        return e;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }

    /** Adapt this row to the publisher contract. */
    public ReferralOutboxRow toOutboxRow() {
        return new ReferralOutboxRow(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
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
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
