package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit row for a single {@code CadreEngine} resolution (shared read-model C9).
 *
 * <p>Persists the inputs, the resolved permitted-workflow set, and the adaptive cockpit spine so every
 * permitted-workflow decision behind a clinical action is replayable. This is an audit log, not a clinical
 * system-of-record.</p>
 */
@Entity
@Table(name = "pct_cadre_decisions")
public class CadreDecisionEntity {

    @Id
    @Column(name = "audit_ref", nullable = false)
    private UUID auditRef;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "in_role")          private String inRole;
    @Column(name = "in_cadre")         private String inCadre;
    @Column(name = "in_scope")         private String inScope;
    @Column(name = "in_visit_type")    private String inVisitType;
    @Column(name = "in_acuity")        private Integer inAcuity;
    @Column(name = "in_context")       private String inContext;
    @Column(name = "in_access_state")  private String inAccessState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permitted_workflows", nullable = false, columnDefinition = "jsonb")
    private String permittedWorkflows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cockpit_spine", nullable = false, columnDefinition = "jsonb")
    private String cockpitSpine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "escalation", nullable = false, columnDefinition = "jsonb")
    private String escalation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getAuditRef() { return auditRef; }
    public void setAuditRef(UUID auditRef) { this.auditRef = auditRef; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public String getInRole() { return inRole; }
    public void setInRole(String inRole) { this.inRole = inRole; }

    public String getInCadre() { return inCadre; }
    public void setInCadre(String inCadre) { this.inCadre = inCadre; }

    public String getInScope() { return inScope; }
    public void setInScope(String inScope) { this.inScope = inScope; }

    public String getInVisitType() { return inVisitType; }
    public void setInVisitType(String inVisitType) { this.inVisitType = inVisitType; }

    public Integer getInAcuity() { return inAcuity; }
    public void setInAcuity(Integer inAcuity) { this.inAcuity = inAcuity; }

    public String getInContext() { return inContext; }
    public void setInContext(String inContext) { this.inContext = inContext; }

    public String getInAccessState() { return inAccessState; }
    public void setInAccessState(String inAccessState) { this.inAccessState = inAccessState; }

    public String getPermittedWorkflows() { return permittedWorkflows; }
    public void setPermittedWorkflows(String permittedWorkflows) { this.permittedWorkflows = permittedWorkflows; }

    public String getCockpitSpine() { return cockpitSpine; }
    public void setCockpitSpine(String cockpitSpine) { this.cockpitSpine = cockpitSpine; }

    public String getEscalation() { return escalation; }
    public void setEscalation(String escalation) { this.escalation = escalation; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
