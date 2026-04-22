package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_audit_event", schema = "vito")
public class ClientAuditEventEntity {

    @Id
    @Column(name = "audit_event_id", nullable = false, updatable = false)
    private UUID auditEventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id")
    private UUID clientHealthId;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_entity", nullable = false)
    private String targetEntity;

    @Column(name = "target_id")
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private String afterState;

    @Column(name = "correlation_id")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance_context", columnDefinition = "jsonb")
    private String provenanceContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (auditEventId == null) {
            auditEventId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getAuditEventId() { return auditEventId; }
    public void setAuditEventId(UUID auditEventId) { this.auditEventId = auditEventId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getProvenanceContext() { return provenanceContext; }
    public void setProvenanceContext(String provenanceContext) { this.provenanceContext = provenanceContext; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
