package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_audit_event", schema = "tuso")
public class FacilityAuditEventEntity {

    @Id
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private FacilityApplicationEntity application;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 128)
    private String actorType;

    @Column(name = "authority_context", length = 128)
    private String authorityContext;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "target_entity_type", nullable = false, length = 64)
    private String targetEntityType;

    @Column(name = "target_entity_id", nullable = false, length = 255)
    private String targetEntityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (auditId == null) {
            auditId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationEntity getApplication() { return application; }
    public void setApplication(FacilityApplicationEntity application) { this.application = application; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAuthorityContext() { return authorityContext; }
    public void setAuthorityContext(String authorityContext) { this.authorityContext = authorityContext; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetEntityType() { return targetEntityType; }
    public void setTargetEntityType(String targetEntityType) { this.targetEntityType = targetEntityType; }
    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }
    public Map<String, Object> getBeforeState() { return beforeState; }
    public void setBeforeState(Map<String, Object> beforeState) { this.beforeState = beforeState; }
    public Map<String, Object> getAfterState() { return afterState; }
    public void setAfterState(Map<String, Object> afterState) { this.afterState = afterState; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
