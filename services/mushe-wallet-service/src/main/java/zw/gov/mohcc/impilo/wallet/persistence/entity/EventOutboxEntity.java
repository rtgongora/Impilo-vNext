package zw.gov.mohcc.impilo.wallet.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code mushe.event_outbox} table.
 *
 * <p>Implements the transactional outbox pattern for reliable Kafka event delivery.
 * Events are written to this table within the same transaction as the wallet mutation,
 * then asynchronously polled and published to Kafka.</p>
 */
@Entity
@Table(name = "event_outbox", schema = "mushe")
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
    private int schemaVersion = 1;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer = "mushe-wallet-service";

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

    @JdbcTypeCode(SqlTypes.JSON)


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
            producer = "mushe-wallet-service";
        }
        if (podId == null) {
            podId = "national-spine";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Adapts this JPA entity to the {@link CompanionOutboxPublisher.OutboxRow} interface
     * expected by the shared-kernel outbox publisher.
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

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
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

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
