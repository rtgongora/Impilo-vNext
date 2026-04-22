package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_audit_events")
public class AuditEventEntity {

    @Id
    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "actor_ref", length = 255)
    private String actorRef;

    @Column(name = "actor_type", length = 64)
    private String actorType;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "before_json", columnDefinition = "JSONB")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "JSONB")
    private String afterJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditEventEntity() {}

    public AuditEventEntity(UUID auditId, UUID tenantId, UUID siteId, String actorRef, String actorType,
                            String action, String targetType, String targetId, UUID correlationId) {
        this.auditId = auditId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.actorRef = actorRef;
        this.actorType = actorType;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.correlationId = correlationId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getAuditId() { return auditId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getActorRef() { return actorRef; }
    public String getActorType() { return actorType; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public UUID getCorrelationId() { return correlationId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

