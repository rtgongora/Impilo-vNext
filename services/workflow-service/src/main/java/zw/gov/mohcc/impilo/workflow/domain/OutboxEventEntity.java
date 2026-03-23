package zw.gov.mohcc.impilo.workflow.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wf_event_outbox")
public class OutboxEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private UUID eventId = UUID.randomUUID();
    @Column(name = "aggregate_type", nullable = false, length = 64) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 255) private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 128) private String eventType;
    @Column(name = "schema_version", nullable = false, length = 16) private String schemaVersion = "1";
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "causation_id") private UUID causationId;
    @Column(name = "idempotency_key", nullable = false, length = 255) private String idempotencyKey;
    @Column(name = "producer", nullable = false, length = 64) private String producer = "workflow-service";
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "subject_id", nullable = false, length = 255) private String subjectId;
    @Column(name = "subject_type", nullable = false, length = 64) private String subjectType;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "partition_key", length = 255) private String partitionKey;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "published_at") private OffsetDateTime publishedAt;

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
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public String getPartitionKey() { return partitionKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }

    public void setAggregateType(String v) { this.aggregateType = v; }
    public void setAggregateId(String v) { this.aggregateId = v; }
    public void setEventType(String v) { this.eventType = v; }
    public void setSchemaVersion(String v) { this.schemaVersion = v; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public void setCausationId(UUID v) { this.causationId = v; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public void setProducer(String v) { this.producer = v; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public void setPodId(String v) { this.podId = v; }
    public void setSubjectId(String v) { this.subjectId = v; }
    public void setSubjectType(String v) { this.subjectType = v; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public void setPartitionKey(String v) { this.partitionKey = v; }
    public void setPublishedAt(OffsetDateTime v) { this.publishedAt = v; }
}
