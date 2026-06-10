package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_access_request")
public class AccessRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_type", nullable = false, length = 128)
    private String requestType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @Column(name = "target_subject_id")
    private String targetSubjectId;

    @Column(name = "requested_role")
    private String requestedRole;

    @Column(name = "requested_scope", columnDefinition = "TEXT")
    private String requestedScope;

    @Column(name = "requested_environment", length = 64)
    private String requestedEnvironment;

    @Column(name = "requested_data_scope", length = 128)
    private String requestedDataScope;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "policy_precheck_result", length = 64)
    private String policyPrecheckResult;

    @Column(name = "approvals_required", columnDefinition = "TEXT")
    private String approvalsRequired;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "audit_trail", columnDefinition = "TEXT")
    private String auditTrail;

    @Column(name = "fallback_request_id", length = 64)
    private String fallbackRequestId;

    @Column(name = "downstream_correlation_id", length = 128)
    private String downstreamCorrelationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AccessRequestEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public String getTargetSubjectId() { return targetSubjectId; }
    public void setTargetSubjectId(String targetSubjectId) { this.targetSubjectId = targetSubjectId; }
    public String getRequestedRole() { return requestedRole; }
    public void setRequestedRole(String requestedRole) { this.requestedRole = requestedRole; }
    public String getRequestedScope() { return requestedScope; }
    public void setRequestedScope(String requestedScope) { this.requestedScope = requestedScope; }
    public String getRequestedEnvironment() { return requestedEnvironment; }
    public void setRequestedEnvironment(String requestedEnvironment) { this.requestedEnvironment = requestedEnvironment; }
    public String getRequestedDataScope() { return requestedDataScope; }
    public void setRequestedDataScope(String requestedDataScope) { this.requestedDataScope = requestedDataScope; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getPolicyPrecheckResult() { return policyPrecheckResult; }
    public void setPolicyPrecheckResult(String policyPrecheckResult) { this.policyPrecheckResult = policyPrecheckResult; }
    public String getApprovalsRequired() { return approvalsRequired; }
    public void setApprovalsRequired(String approvalsRequired) { this.approvalsRequired = approvalsRequired; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getAuditTrail() { return auditTrail; }
    public void setAuditTrail(String auditTrail) { this.auditTrail = auditTrail; touch(); }
    public String getFallbackRequestId() { return fallbackRequestId; }
    public void setFallbackRequestId(String fallbackRequestId) { this.fallbackRequestId = fallbackRequestId; }
    public String getDownstreamCorrelationId() { return downstreamCorrelationId; }
    public void setDownstreamCorrelationId(String downstreamCorrelationId) { this.downstreamCorrelationId = downstreamCorrelationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void initTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
