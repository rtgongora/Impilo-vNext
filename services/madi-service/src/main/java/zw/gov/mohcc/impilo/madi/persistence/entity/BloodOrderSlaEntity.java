package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An SLA timer for a blood-order stage (crossmatch / issue). OPEN until met (stage completed in
 * time) or breached (target passed while still open).
 */
@Entity
@Table(name = "blood_order_sla")
public class BloodOrderSlaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "sla_id", nullable = false)
    private UUID slaId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "target_at", nullable = false)
    private OffsetDateTime targetAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "breached_at")
    private OffsetDateTime breachedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = OffsetDateTime.now();
        createdAt = OffsetDateTime.now();
    }

    public UUID getSlaId() { return slaId; }
    public void setSlaId(UUID slaId) { this.slaId = slaId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public OffsetDateTime getTargetAt() { return targetAt; }
    public void setTargetAt(OffsetDateTime targetAt) { this.targetAt = targetAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getBreachedAt() { return breachedAt; }
    public void setBreachedAt(OffsetDateTime breachedAt) { this.breachedAt = breachedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
