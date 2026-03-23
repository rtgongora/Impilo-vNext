package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_event_outbox")
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
    private String schemaVersion = "1.0";

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = "coverage-service";

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    public OutboxEventEntity() {}

    public static OutboxEventEntity create(
            String aggregateType, String aggregateId,
            String eventType, UUID correlationId,
            UUID tenantId, String podId,
            String subjectId, String subjectType,
            String payloadJson) {

        OutboxEventEntity e = new OutboxEventEntity();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.correlationId = correlationId;
        e.tenantId = tenantId;
        e.podId = podId;
        e.subjectId = subjectId;
        e.subjectType = subjectType;
        e.occurredAt = OffsetDateTime.now();
        e.payloadJson = payloadJson;
        e.idempotencyKey = "coverage-service:" + subjectType + ":" + subjectId + ":" + eventType;
        return e;
    }

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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
