package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks SLA (Service Level Agreement) timers for order stages.
 * Each timer monitors whether a specific order stage completes
 * within the target turnaround time.
 */
@Entity
@Table(name = "oros_sla_timers")
public class SlaTimerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "timer_id", nullable = false)
    private UUID timerId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "stage", nullable = false, length = 30)
    private String stage;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "target_at")
    private OffsetDateTime targetAt;

    @Column(name = "breached", nullable = false)
    private boolean breached = false;

    @Column(name = "breached_at")
    private OffsetDateTime breachedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    public UUID getTimerId() { return timerId; }
    public void setTimerId(UUID timerId) { this.timerId = timerId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getTargetAt() { return targetAt; }
    public void setTargetAt(OffsetDateTime targetAt) { this.targetAt = targetAt; }

    public boolean isBreached() { return breached; }
    public void setBreached(boolean breached) { this.breached = breached; }

    public OffsetDateTime getBreachedAt() { return breachedAt; }
    public void setBreachedAt(OffsetDateTime breachedAt) { this.breachedAt = breachedAt; }
}
