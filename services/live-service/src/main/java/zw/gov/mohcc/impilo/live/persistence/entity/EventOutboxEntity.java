package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_outbox", schema = "live")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "subject_type", length = 64)
    private String subjectType;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "pod_id", length = 64)
    private String podId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "partition_key")
    private String partitionKey;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override
            public Long id() {
                return self.id;
            }

            @Override
            public String aggregateType() {
                return self.aggregateType;
            }

            @Override
            public String aggregateId() {
                return self.aggregateId;
            }

            @Override
            public String eventType() {
                return self.eventType;
            }

            @Override
            public String payloadJson() {
                return self.payloadJson;
            }

            @Override
            public OffsetDateTime occurredAt() {
                return self.occurredAt;
            }

            @Override
            public OffsetDateTime publishedAt() {
                return self.publishedAt;
            }

            @Override
            public String tenantId() {
                return self.tenantId != null ? self.tenantId.toString() : null;
            }

            @Override
            public String podId() {
                return self.podId;
            }

            @Override
            public String correlationId() {
                return self.correlationId != null ? self.correlationId.toString() : null;
            }

            @Override
            public String causationId() {
                return null;
            }

            @Override
            public String idempotencyKey() {
                return self.idempotencyKey;
            }

            @Override
            public int schemaVersion() {
                return 1;
            }

            @Override
            public String subjectType() {
                return self.subjectType != null ? self.subjectType : self.aggregateType;
            }

            @Override
            public String subjectId() {
                return self.subjectId != null ? self.subjectId : self.aggregateId;
            }

            @Override
            public String partitionKey() {
                return self.partitionKey != null ? self.partitionKey : self.aggregateId;
            }

            @Override
            public int retryCount() {
                return 0;
            }
        };
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
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
}
