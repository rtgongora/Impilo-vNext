package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_decision_log", schema = "tshepo")
public class PolicyDecisionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "purpose_of_use", nullable = false)
    private String purposeOfUse;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    private String decision;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(columnDefinition = "jsonb")
    private String obligations;

    @Column(name = "risk_score")
    private Short riskScore;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "policy_version")
    private String policyVersion;

    @Column(name = "reason_codes")
    private String reasonCodes;

    // Health OS §6, §12: Provider ID and Subject ID for doctrine-aligned audit
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "subject_id")
    private String subjectId;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getObligations() { return obligations; }
    public void setObligations(String obligations) { this.obligations = obligations; }
    public Short getRiskScore() { return riskScore; }
    public void setRiskScore(Short riskScore) { this.riskScore = riskScore; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String reasonCodes) { this.reasonCodes = reasonCodes; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
}
