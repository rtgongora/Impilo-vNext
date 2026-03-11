package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dgv_decision_audit")
public class DecisionAuditEntity {

    @Id
    @Column(name = "decision_id", nullable = false)
    private UUID decisionId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "dataset_name", nullable = false, length = 255)
    private String datasetName;

    @Column(name = "principal_id", nullable = false, length = 255)
    private String principalId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason_codes_json", nullable = false, columnDefinition = "TEXT")
    private String reasonCodesJson = "[]";

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(name = "query_fingerprint", length = 255)
    private String queryFingerprint;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    protected DecisionAuditEntity() {}

    public UUID getDecisionId() { return decisionId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDatasetId() { return datasetId; }
    public String getDatasetName() { return datasetName; }
    public String getPrincipalId() { return principalId; }
    public String getDecision() { return decision; }
    public String getReasonCodesJson() { return reasonCodesJson; }
    public String getPolicyVersion() { return policyVersion; }
    public String getQueryFingerprint() { return queryFingerprint; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setReasonCodesJson(String reasonCodesJson) { this.reasonCodesJson = reasonCodesJson; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public void setQueryFingerprint(String queryFingerprint) { this.queryFingerprint = queryFingerprint; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
