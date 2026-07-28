package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mh_risk_formulation")
public class RiskFormulationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "risk_to_self", nullable = false)
    private String riskToSelf;

    @Column(name = "risk_to_others", nullable = false)
    private String riskToOthers;

    @Column(name = "risk_summary", nullable = false)
    private String riskSummary;

    @Column(name = "formulated_by", nullable = false)
    private String formulatedBy;

    @Column(name = "formulated_at", nullable = false)
    private OffsetDateTime formulatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID v) { this.assessmentId = v; }
    public String getRiskToSelf() { return riskToSelf; }
    public void setRiskToSelf(String v) { this.riskToSelf = v; }
    public String getRiskToOthers() { return riskToOthers; }
    public void setRiskToOthers(String v) { this.riskToOthers = v; }
    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String v) { this.riskSummary = v; }
    public String getFormulatedBy() { return formulatedBy; }
    public void setFormulatedBy(String v) { this.formulatedBy = v; }
    public OffsetDateTime getFormulatedAt() { return formulatedAt; }
    public void setFormulatedAt(OffsetDateTime v) { this.formulatedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
