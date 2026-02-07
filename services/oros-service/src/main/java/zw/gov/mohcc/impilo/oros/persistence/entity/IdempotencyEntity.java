package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Ensures at-most-once processing for inbound requests and events.
 * The idempotency key is the primary key (e.g., Kafka message key or request ID).
 */
@Entity
@Table(name = "oros_idempotency")
public class IdempotencyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "source_system")
    private String sourceSystem;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    // Getters and setters

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
