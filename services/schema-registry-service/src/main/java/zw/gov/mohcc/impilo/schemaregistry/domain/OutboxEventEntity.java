package zw.gov.mohcc.impilo.schemaregistry.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scr_event_outbox")
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
    @Column(name = "producer", nullable = false) private String producer = "schema-registry-service";
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
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public void setSubjectId(String v) { this.subjectId = v; }
    public void setSubjectType(String v) { this.subjectType = v; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
}
