package zw.gov.mohcc.impilo.air.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

@Entity
@Table(name = "event_outbox", schema = "ai_registry")
public class AiEventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "correlation_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID correlationId;

    @Column(name = "causation_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = "ai-model-registry-service";

    @Column(name = "tenant_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
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

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (producer == null) {
            producer = "ai-model-registry-service";
        }
        if (podId == null) {
            podId = "national-spine";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final AiEventOutboxEntity self = this;
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
                return self.causationId != null ? self.causationId.toString() : null;
            }

            @Override
            public String idempotencyKey() {
                return self.idempotencyKey;
            }

            @Override
            public int schemaVersion() {
                return self.schemaVersion;
            }

            @Override
            public String subjectType() {
                return self.subjectType;
            }

            @Override
            public String subjectId() {
                return self.subjectId;
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

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public void setCausationId(UUID causationId) {
        this.causationId = causationId;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public void setPublishError(String publishError) {
        this.publishError = publishError;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }
}
