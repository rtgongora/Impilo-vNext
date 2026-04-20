package zw.gov.mohcc.impilo.docstore.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

/**
 * JPA entity for the document_store.event_outbox table.
 *
 * <p>Transactional outbox for Kafka publishing via DocumentOutboxPublisher
 * (CompanionOutboxPublisher). Carries v1.1 envelope context for dual emit.</p>
 */
@Entity
@Table(name = "event_outbox", schema = "document_store")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "tenant_id") private String tenantId;
    @Column(name = "pod_id") private String podId;
    @Column(name = "correlation_id") private String correlationId;
    @Column(name = "causation_id") private String causationId;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "schema_version") private Integer schemaVersion;
    @Column(name = "producer", length = 64) private String producer;
    @Column(name = "subject_type", length = 64) private String subjectType;
    @Column(name = "subject_id") private String subjectId;
    @Column(name = "partition_key") private String partitionKey;
    @Column(name = "occurred_at") private OffsetDateTime occurredAt;
    @Column(name = "publish_error") private String publishError;
    @Column(name = "retry_count") private Integer retryCount;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
        if (schemaVersion == null) {
            schemaVersion = 1;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override public Long id() { return self.id; }
            @Override public String aggregateType() { return self.aggregateType; }
            @Override public String aggregateId() { return self.aggregateId; }
            @Override public String eventType() { return self.eventType; }
            @Override public String payloadJson() { return self.payload; }
            @Override public OffsetDateTime occurredAt() {
                if (self.occurredAt != null) {
                    return self.occurredAt;
                }
                return self.createdAt != null ? self.createdAt : OffsetDateTime.now(ZoneOffset.UTC);
            }
            @Override public OffsetDateTime publishedAt() { return self.publishedAt; }
            @Override public String tenantId() { return self.tenantId; }
            @Override public String podId() { return self.podId; }
            @Override public String correlationId() { return self.correlationId; }
            @Override public String causationId() { return self.causationId; }
            @Override public String idempotencyKey() { return self.idempotencyKey; }
            @Override public int schemaVersion() { return self.schemaVersion != null ? self.schemaVersion : 1; }
            @Override public String subjectType() { return self.subjectType != null ? self.subjectType : self.aggregateType; }
            @Override public String subjectId() { return self.subjectId != null ? self.subjectId : self.aggregateId; }
            @Override public String partitionKey() { return self.partitionKey != null ? self.partitionKey : self.aggregateId; }
            @Override public int retryCount() { return self.retryCount != null ? self.retryCount : 0; }
        };
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

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
