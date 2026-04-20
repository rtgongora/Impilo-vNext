package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_outbox", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class EventOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventId;
    @Column(nullable = false, length = 64)
    private String aggregateType;
    @Column(nullable = false)
    private String aggregateId;
    @Column(nullable = false, length = 128)
    private String eventType;
    private int schemaVersion = 1;
    private UUID correlationId;
    private UUID causationId;
    @Column(nullable = false, length = 255)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String producer = "hr-payroll-service";
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 64)
    private String podId = "national-spine";
    @Column(nullable = false)
    private String subjectId;
    @Column(nullable = false, length = 64)
    private String subjectType;
    private String partitionKey;
    private OffsetDateTime occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payloadJson;
    @Column(columnDefinition = "TEXT")
    private String publishError;
    private int retryCount;
    private OffsetDateTime createdAt;
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
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity self = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override
            public Long id() {
                return self.getId();
            }

            @Override
            public String aggregateType() {
                return self.getAggregateType();
            }

            @Override
            public String aggregateId() {
                return self.getAggregateId();
            }

            @Override
            public String eventType() {
                return self.getEventType();
            }

            @Override
            public String payloadJson() {
                return self.getPayloadJson();
            }

            @Override
            public OffsetDateTime occurredAt() {
                return self.getOccurredAt();
            }

            @Override
            public OffsetDateTime publishedAt() {
                return self.getPublishedAt();
            }

            @Override
            public String tenantId() {
                return self.getTenantId() != null ? self.getTenantId().toString() : null;
            }

            @Override
            public String podId() {
                return self.getPodId();
            }

            @Override
            public String correlationId() {
                return self.getCorrelationId() != null ? self.getCorrelationId().toString() : null;
            }

            @Override
            public String causationId() {
                return self.getCausationId() != null ? self.getCausationId().toString() : null;
            }

            @Override
            public String idempotencyKey() {
                return self.getIdempotencyKey();
            }

            @Override
            public int schemaVersion() {
                return self.getSchemaVersion();
            }

            @Override
            public String subjectType() {
                return self.getSubjectType() != null ? self.getSubjectType() : self.getAggregateType();
            }

            @Override
            public String subjectId() {
                return self.getSubjectId() != null ? self.getSubjectId() : self.getAggregateId();
            }

            @Override
            public String partitionKey() {
                return self.getPartitionKey() != null ? self.getPartitionKey() : self.getAggregateId();
            }

            @Override
            public int retryCount() {
                return self.getRetryCount();
            }
        };
    }
}
