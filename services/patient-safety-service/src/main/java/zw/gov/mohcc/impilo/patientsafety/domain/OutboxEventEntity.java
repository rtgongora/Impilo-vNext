package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Transactional outbox row for patient-safety domain events. */
@Entity
@Table(name = "ps_event_outbox")
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
    private String producer = "patient-safety-service";

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "publish_error", columnDefinition = "TEXT")
    private String publishError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public OutboxEventEntity() {}

    /**
     * Creates an outbox row for {@link CompanionOutboxPublisher}. {@code payloadJson} must be the
     * canonical JSON object for the event payload (not a nested EventEnvelope).
     */
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
        if (podId != null && !podId.isBlank()) e.podId = podId;
        e.subjectId = subjectId;
        e.subjectType = subjectType;
        e.occurredAt = OffsetDateTime.now();
        e.payloadJson = payloadJson;
        e.idempotencyKey = "patient-safety-service:"
                + aggregateType + ":"
                + aggregateId + ":"
                + eventType + ":"
                + (correlationId != null ? correlationId : UUID.randomUUID());
        return e;
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final OutboxEventEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override public Long id() { return self.id; }
            @Override public String aggregateType() { return self.aggregateType; }
            @Override public String aggregateId() { return self.aggregateId; }
            @Override public String eventType() { return self.eventType; }
            @Override public String payloadJson() { return self.payloadJson; }
            @Override public OffsetDateTime occurredAt() { return self.occurredAt; }
            @Override public OffsetDateTime publishedAt() { return self.publishedAt; }
            @Override public String tenantId() { return self.tenantId != null ? self.tenantId.toString() : null; }
            @Override public String podId() { return self.podId; }
            @Override public String correlationId() { return self.correlationId != null ? self.correlationId.toString() : null; }
            @Override public String idempotencyKey() { return self.idempotencyKey; }
            @Override public int schemaVersion() {
                if (self.schemaVersion == null) return 1;
                try {
                    int dot = self.schemaVersion.indexOf('.');
                    if (dot > 0) return Integer.parseInt(self.schemaVersion.substring(0, dot));
                    return Integer.parseInt(self.schemaVersion);
                } catch (Exception ex) { return 1; }
            }
            @Override public String subjectType() { return self.subjectType; }
            @Override public String subjectId() { return self.subjectId; }
            @Override public int retryCount() { return self.retryCount; }
        };
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getSubjectId() { return subjectId; }
    public String getSubjectType() { return subjectType; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishError() { return publishError; }
    public void setPublishError(String publishError) { this.publishError = publishError; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
