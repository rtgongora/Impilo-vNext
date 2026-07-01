package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Audit log row for a FormResolver resolution (mirrors CadreDecisionEntity). */
@Entity
@Table(name = "pct_form_resolver_decisions")
public class FormResolverDecisionEntity {

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

    @Column(name = "cadre_audit_ref")
    private UUID cadreAuditRef;

    @Column(name = "in_cadre")        private String inCadre;
    @Column(name = "in_context")      private String inContext;
    @Column(name = "in_care_setting") private String inCareSetting;
    @Column(name = "in_care_stage")   private String inCareStage;
    @Column(name = "in_specialty")    private String inSpecialty;
    @Column(name = "in_acuity")       private Integer inAcuity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "in_patient_context", columnDefinition = "jsonb")
    private String inPatientContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mandatory", nullable = false, columnDefinition = "jsonb")
    private String mandatory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended", nullable = false, columnDefinition = "jsonb")
    private String recommended;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optional", nullable = false, columnDefinition = "jsonb")
    private String optional;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prohibited", nullable = false, columnDefinition = "jsonb")
    private String prohibited;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "countersign_required", nullable = false, columnDefinition = "jsonb")
    private String countersignRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (auditRef == null) {
            auditRef = UUID.randomUUID();
        }
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

    public UUID getCadreAuditRef() { return cadreAuditRef; }
    public void setCadreAuditRef(UUID cadreAuditRef) { this.cadreAuditRef = cadreAuditRef; }

    public String getInCadre() { return inCadre; }
    public void setInCadre(String inCadre) { this.inCadre = inCadre; }

    public String getInContext() { return inContext; }
    public void setInContext(String inContext) { this.inContext = inContext; }

    public String getInCareSetting() { return inCareSetting; }
    public void setInCareSetting(String inCareSetting) { this.inCareSetting = inCareSetting; }

    public String getInCareStage() { return inCareStage; }
    public void setInCareStage(String inCareStage) { this.inCareStage = inCareStage; }

    public String getInSpecialty() { return inSpecialty; }
    public void setInSpecialty(String inSpecialty) { this.inSpecialty = inSpecialty; }

    public Integer getInAcuity() { return inAcuity; }
    public void setInAcuity(Integer inAcuity) { this.inAcuity = inAcuity; }

    public String getInPatientContext() { return inPatientContext; }
    public void setInPatientContext(String inPatientContext) { this.inPatientContext = inPatientContext; }

    public String getMandatory() { return mandatory; }
    public void setMandatory(String mandatory) { this.mandatory = mandatory; }

    public String getRecommended() { return recommended; }
    public void setRecommended(String recommended) { this.recommended = recommended; }

    public String getOptional() { return optional; }
    public void setOptional(String optional) { this.optional = optional; }

    public String getProhibited() { return prohibited; }
    public void setProhibited(String prohibited) { this.prohibited = prohibited; }

    public String getCountersignRequired() { return countersignRequired; }
    public void setCountersignRequired(String countersignRequired) { this.countersignRequired = countersignRequired; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
