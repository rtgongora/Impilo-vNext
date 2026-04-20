package zw.gov.mohcc.impilo.tshepo.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for {@code tshepo_audit.event_outbox} — transactional outbox for v1.1 Kafka events.
 */
@Entity
@Table(name = "event_outbox", schema = "tshepo_audit")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", insertable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion = 1;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = "tshepo-audit-service";

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

    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at", columnDefinition = "TIMESTAMPTZ")
    private Instant publishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (producer == null) {
            producer = "tshepo-audit-service";
        }
        if (podId == null) {
            podId = "national-spine";
        }
        if (schemaVersion == null) {
            schemaVersion = 1;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Adapt this JPA entity to the shared-kernel {@link CompanionOutboxPublisher.OutboxRow} contract.
     */
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
                return self.payloadJson != null ? self.payloadJson : "{}";
            }

            @Override
            public OffsetDateTime occurredAt() {
                if (self.occurredAt != null) {
                    return self.occurredAt;
                }
                return self.createdAt != null ? self.createdAt.atOffset(ZoneOffset.UTC) : null;
            }

            @Override
            public OffsetDateTime publishedAt() {
                return self.publishedAt != null ? self.publishedAt.atOffset(ZoneOffset.UTC) : null;
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
                return self.causationId != null ? self.causationId.toString() : null;
            }

            @Override
            public String idempotencyKey() {
                return self.idempotencyKey;
            }

            @Override
            public int schemaVersion() {
                return self.schemaVersion != null ? self.schemaVersion : 1;
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
                return self.retryCount != null ? self.retryCount : 0;
            }
        };
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public void setCausationId(UUID causationId) {
        this.causationId = causationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPodId() {
        return podId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getPublishError() {
        return publishError;
    }

    public void setPublishError(String publishError) {
        this.publishError = publishError;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
