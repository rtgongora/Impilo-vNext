package zw.gov.mohcc.impilo.procurement.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import zw.gov.mohcc.impilo.sharedkernel.events.CompanionOutboxPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_outbox", schema = "proc")
@Getter
@Setter
@NoArgsConstructor
public class EventOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private int schemaVersion = 1;
    private UUID correlationId;
    private UUID causationId;
    private String idempotencyKey;
    private String producer = "procurement-service";
    private UUID tenantId;
    private String podId = "national-spine";
    private String subjectId;
    private String subjectType;
    private String partitionKey;
    private OffsetDateTime occurredAt;
    @Column(columnDefinition = "jsonb")
    private String payloadJson;
    @Column(columnDefinition = "TEXT")
    private String publishError;
    private int retryCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime n = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = n;
        }
        if (occurredAt == null) {
            occurredAt = n;
        }
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
    }

    public CompanionOutboxPublisher.OutboxRow toOutboxRow() {
        final EventOutboxEntity s = this;
        return new CompanionOutboxPublisher.OutboxRow() {
            @Override
            public Long id() {
                return s.getId();
            }

            @Override
            public String aggregateType() {
                return s.getAggregateType();
            }

            @Override
            public String aggregateId() {
                return s.getAggregateId();
            }

            @Override
            public String eventType() {
                return s.getEventType();
            }

            @Override
            public String payloadJson() {
                return s.getPayloadJson();
            }

            @Override
            public OffsetDateTime occurredAt() {
                return s.getOccurredAt();
            }

            @Override
            public OffsetDateTime publishedAt() {
                return s.getPublishedAt();
            }

            @Override
            public String tenantId() {
                return s.getTenantId() != null ? s.getTenantId().toString() : null;
            }

            @Override
            public String podId() {
                return s.getPodId();
            }

            @Override
            public String correlationId() {
                return s.getCorrelationId() != null ? s.getCorrelationId().toString() : null;
            }

            @Override
            public String causationId() {
                return s.getCausationId() != null ? s.getCausationId().toString() : null;
            }

            @Override
            public String idempotencyKey() {
                return s.getIdempotencyKey();
            }

            @Override
            public int schemaVersion() {
                return s.getSchemaVersion();
            }

            @Override
            public String subjectType() {
                return s.getSubjectType() != null ? s.getSubjectType() : s.getAggregateType();
            }

            @Override
            public String subjectId() {
                return s.getSubjectId() != null ? s.getSubjectId() : s.getAggregateId();
            }

            @Override
            public String partitionKey() {
                return s.getPartitionKey() != null ? s.getPartitionKey() : s.getAggregateId();
            }

            @Override
            public int retryCount() {
                return s.getRetryCount();
            }
        };
    }
}
