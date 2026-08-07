package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox entity for reliable Kafka event publishing.
 * Events are written within the same transaction as the business operation
 * and polled by a separate publisher process.
 */
@Entity
@Table(name = "inv_event_outbox")
public class EventOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount != null ? retryCount : 0; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    /**
     * Adapts this row for {@link zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher}.
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
                return self.payload != null ? self.payload : "{}";
            }

            @Override
            public OffsetDateTime occurredAt() {
                return self.createdAt != null ? self.createdAt : OffsetDateTime.now();
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
                // Was "inventory-service" — a producer name in the pod_id field. pod_id names
                // the federation pod, so FederationAuthority.isNational() returned false for
                // every event inventory published, and any pod-based routing saw a pod that
                // does not exist. "national" is the literal isNational() recognises.
                return "national";
            }

            @Override
            public int retryCount() {
                return self.retryCount != null ? self.retryCount : 0;
            }
        };
    }
}
