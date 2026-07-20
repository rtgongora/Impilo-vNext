package zw.gov.mohcc.impilo.ubomi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code ubomi.event_outbox} table (transactional outbox).
 *
 * <p>Rows are written within the same transaction as the birth/death/CRVS
 * business operation and drained by
 * {@link zw.gov.mohcc.impilo.ubomi.events.UbomiOutboxPublisher} (W1a), which
 * mirrors {@code ButanoOutboxPublisher} on the shared
 * {@link CompanionOutboxPublisher} base. The v1.1 context columns and the
 * {@code retry_count}/{@code publish_error} poison-row columns were added in
 * V004 to support that base.</p>
 */
@Entity
@Table(name = "event_outbox", schema = "ubomi")
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
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    // v1.1 context columns (V004)
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "pod_id", length = 128) private String podId;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Column(name = "causation_id", length = 128) private String causationId;
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    @Column(name = "schema_version") private Integer schemaVersion;
    @Column(name = "producer", length = 64) private String producer;
    @Column(name = "subject_type", length = 64) private String subjectType;
    @Column(name = "subject_id", length = 128) private String subjectId;
    @Column(name = "partition_key", length = 128) private String partitionKey;
    @Column(name = "occurred_at") private OffsetDateTime occurredAt;
    @Column(name = "publish_error", columnDefinition = "TEXT") private String publishError;
    @Column(name = "retry_count") private Integer retryCount;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /**
     * Adapt this JPA entity to the shared-kernel {@code OutboxRow} contract so
     * the shared publishing loop can emit legacy + v1.1 events from it.
     */
    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override public Long id() { return self.id; }
            @Override public String aggregateType() { return self.aggregateType; }
            @Override public String aggregateId() { return self.aggregateId; }
            @Override public String eventType() { return self.eventType; }
            @Override public String payloadJson() { return self.payload; }
            @Override public OffsetDateTime occurredAt() {
                return self.occurredAt != null ? self.occurredAt : self.createdAt;
            }
            @Override public OffsetDateTime publishedAt() { return self.publishedAt; }
            @Override public String tenantId() {
                return self.tenantId != null ? self.tenantId.toString() : null;
            }
            @Override public String podId() { return self.podId; }
            @Override public String correlationId() { return self.correlationId; }
            @Override public String causationId() { return self.causationId; }
            @Override public String idempotencyKey() { return self.idempotencyKey; }
            @Override public int schemaVersion() {
                return self.schemaVersion != null ? self.schemaVersion : 1;
            }
            @Override public String subjectType() {
                return self.subjectType != null ? self.subjectType : self.aggregateType;
            }
            @Override public String subjectId() {
                return self.subjectId != null ? self.subjectId : self.aggregateId;
            }
            @Override public String partitionKey() {
                return self.partitionKey != null ? self.partitionKey : self.aggregateId;
            }
            @Override public int retryCount() {
                return self.retryCount != null ? self.retryCount : 0;
            }
        };
    }

    // Getters and setters

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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getCausationId() { return causationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getProducer() { return producer; }
    public void setProducer(String producer) { this.producer = producer; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
}
