package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * A money-critical Kafka event that exhausted its retries and landed on
 * {@code costa.money.dlq}. Kept durable and queryable so operations can see
 * exactly which settlements/refunds/charges were dropped and replay them
 * (replay is safe — every money consumer is idempotent).
 */
@Entity
@Table(name = "costa_failed_money_events")
public class FailedMoneyEventEntity {

    public static final String STATUS_DEAD = "DEAD";
    public static final String STATUS_REPLAYED = "REPLAYED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", nullable = false, length = 200)
    private String originalTopic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_DEAD;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "replayed_at")
    private OffsetDateTime replayedAt;

    @Column(name = "replayed_by", length = 120)
    private String replayedBy;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalTopic() { return originalTopic; }
    public void setOriginalTopic(String originalTopic) { this.originalTopic = originalTopic; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getReplayedAt() { return replayedAt; }
    public void setReplayedAt(OffsetDateTime replayedAt) { this.replayedAt = replayedAt; }
    public String getReplayedBy() { return replayedBy; }
    public void setReplayedBy(String replayedBy) { this.replayedBy = replayedBy; }
}
