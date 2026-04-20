package zw.gov.mohcc.impilo.butanofhir.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "event_outbox", schema = "butano_fhir")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "pod_id")
    private String podId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "causation_id")
    private String causationId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion = 1;

    @Column(name = "producer", nullable = false)
    private String producer = "butano-fhir";

    @Column(name = "subject_type")
    private String subjectType;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "partition_key")
    private String partitionKey;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "published", nullable = false)
    private Boolean published = false;

    @Column(name = "publish_error")
    private String publishError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
    }

    /**
     * Returns a summary map suitable for outbox relay publishing.
     */
    public java.util.Map<String, Object> toOutboxRow() {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", id);
        row.put("tenant_id", tenantId);
        row.put("pod_id", podId);
        row.put("aggregate_type", aggregateType);
        row.put("aggregate_id", aggregateId);
        row.put("event_type", eventType);
        row.put("payload", payload);
        row.put("correlation_id", correlationId);
        row.put("causation_id", causationId);
        row.put("idempotency_key", idempotencyKey);
        row.put("schema_version", schemaVersion);
        row.put("producer", producer);
        row.put("subject_type", subjectType);
        row.put("subject_id", subjectId);
        row.put("partition_key", partitionKey);
        row.put("occurred_at", occurredAt);
        row.put("published", published);
        row.put("publish_error", publishError);
        row.put("created_at", createdAt);
        return row;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
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
    public Boolean getPublished() { return published; }
    public void setPublished(Boolean published) { this.published = published; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
