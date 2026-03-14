package zw.gov.mohcc.impilo.devportal.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dvp_event_outbox")
public class OutboxEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false) private UUID eventId = UUID.randomUUID();
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private String aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "schema_version", nullable = false) private String schemaVersion = "1";
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "causation_id") private UUID causationId;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "producer", nullable = false) private String producer = "developer-portal-service";
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "pod_id", nullable = false) private String podId = "national-spine";
    @Column(name = "subject_id") private String subjectId;
    @Column(name = "subject_type") private String subjectType;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "payload_json", columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "partition_key") private String partitionKey;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "published_at") private OffsetDateTime publishedAt;

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String v) { this.aggregateType = v; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String v) { this.aggregateId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String v) { this.schemaVersion = v; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public UUID getCausationId() { return causationId; }
    public void setCausationId(UUID v) { this.causationId = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public String getProducer() { return producer; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String v) { this.subjectId = v; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String v) { this.subjectType = v; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String v) { this.partitionKey = v; }
}
