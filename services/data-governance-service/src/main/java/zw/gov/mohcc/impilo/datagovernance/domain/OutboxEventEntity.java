package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dgv_event_outbox")
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId = UUID.randomUUID();

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1";

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = "data-governance-service";

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "partition_key", length = 255)
    private String partitionKey;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public OutboxEventEntity() {}

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getSchemaVersion() { return schemaVersion; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProducer() { return producer; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getSubjectId() { return subjectId; }
    public String getSubjectType() { return subjectType; }
    public String getPartitionKey() { return partitionKey; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }

    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public void setCausationId(UUID causationId) { this.causationId = causationId; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setProducer(String producer) { this.producer = producer; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setPodId(String podId) { this.podId = podId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
