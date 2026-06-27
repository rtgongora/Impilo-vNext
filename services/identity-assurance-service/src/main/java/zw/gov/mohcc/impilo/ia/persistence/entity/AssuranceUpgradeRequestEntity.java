package zw.gov.mohcc.impilo.ia.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.ia.core.AssuranceLevel;
import zw.gov.mohcc.impilo.ia.core.UpgradeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An assurance-level upgrade request and its review lifecycle. */
@Entity
@Table(name = "assurance_upgrade_request", schema = "ia")
public class AssuranceUpgradeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false, length = 8)
    private AssuranceLevel currentLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false, length = 8)
    private AssuranceLevel targetLevel;

    @Column(name = "method", nullable = false, length = 64)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UpgradeStatus status = UpgradeStatus.PENDING;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusDays(30);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public AssuranceLevel getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(AssuranceLevel currentLevel) { this.currentLevel = currentLevel; }
    public AssuranceLevel getTargetLevel() { return targetLevel; }
    public void setTargetLevel(AssuranceLevel targetLevel) { this.targetLevel = targetLevel; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public UpgradeStatus getStatus() { return status; }
    public void setStatus(UpgradeStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
}
