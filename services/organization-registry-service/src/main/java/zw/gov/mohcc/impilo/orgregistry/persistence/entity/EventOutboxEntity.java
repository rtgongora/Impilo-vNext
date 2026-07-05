package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_outbox", schema = "org_registry")
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
    private String producer = "organization-registry-service";

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
}
