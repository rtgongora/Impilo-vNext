package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of every authorization decision.
 *
 * <p>One row is persisted for every call to the PolicyEngine, regardless
 * of verdict. This provides a complete, queryable decision history for
 * compliance, incident response, and anomaly detection.</p>
 */
@Entity
@Table(name = "policy_decision_log", schema = "tshepo_authz")
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

    @Column(name = "actor_type", length = 32)
    private String actorType;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "purpose_of_use", length = 32)
    private String purposeOfUse;

    @Column(nullable = false, length = 16)
    private String verdict;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(columnDefinition = "jsonb")
    private String obligations;

    @Column(name = "deny_reason", columnDefinition = "TEXT")
    private String denyReason;

    @Column(name = "step_up_methods", columnDefinition = "TEXT")
    private String stepUpMethods;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @PrePersist
    protected void onCreate() {
        this.evaluatedAt = Instant.now();
    }

    // ── Getters and Setters ────────────────────────────────────────────

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

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getObligations() { return obligations; }
    public void setObligations(String obligations) { this.obligations = obligations; }

    public String getDenyReason() { return denyReason; }
    public void setDenyReason(String denyReason) { this.denyReason = denyReason; }

    public String getStepUpMethods() { return stepUpMethods; }
    public void setStepUpMethods(String stepUpMethods) { this.stepUpMethods = stepUpMethods; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
