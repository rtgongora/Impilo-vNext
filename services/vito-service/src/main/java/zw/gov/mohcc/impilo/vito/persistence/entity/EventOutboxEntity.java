package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;

@Entity
@Table(name = "event_outbox", schema = "vito")
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

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    // v1.1 context columns (V017 + V018)
    @Column(name = "tenant_id") private String tenantId;
    @Column(name = "pod_id") private String podId;
    @Column(name = "correlation_id") private String correlationId;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "causation_id") private String causationId;
    @Column(name = "schema_version") private Integer schemaVersion;
    @Column(name = "producer", length = 64) private String producer;
    @Column(name = "subject_type", length = 64) private String subjectType;
    @Column(name = "subject_id") private String subjectId;
    @Column(name = "partition_key") private String partitionKey;
    @Column(name = "occurred_at") private OffsetDateTime occurredAt;
    @Column(name = "publish_error") private String publishError;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
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
                return self.occurredAt != null ? self.occurredAt : self.createdAt;
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCausationId() { return causationId; }
    public void setCausationId(String causationId) { this.causationId = causationId; }
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
}
