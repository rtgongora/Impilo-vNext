package zw.gov.mohcc.impilo.ndr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_query_audit")
public class QueryAuditEntity {

    @Id
    @Column(name = "audit_id")
    private UUID auditId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "principal_id", nullable = false, length = 255)
    private String principalId;

    @Column(name = "dataset", nullable = false, length = 255)
    private String dataset;

    @Column(name = "purpose_of_use", nullable = false, length = 64)
    private String purposeOfUse;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason_codes_json", columnDefinition = "TEXT")
    private String reasonCodesJson;

    @Column(name = "policy_version", length = 64)
    private String policyVersion;

    @Column(name = "query_type", length = 64)
    private String queryType;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    protected QueryAuditEntity() {}

    public QueryAuditEntity(UUID tenantId, String principalId, String dataset,
                            String purposeOfUse, String decision, String reasonCodesJson,
                            String policyVersion, String queryType, String correlationId) {
        this.tenantId = tenantId;
        this.principalId = principalId;
        this.dataset = dataset;
        this.purposeOfUse = purposeOfUse;
        this.decision = decision;
        this.reasonCodesJson = reasonCodesJson;
        this.policyVersion = policyVersion;
        this.queryType = queryType;
        this.correlationId = correlationId;
    }

    public UUID getAuditId() { return auditId; }
    public UUID getTenantId() { return tenantId; }
    public String getPrincipalId() { return principalId; }
    public String getDataset() { return dataset; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public String getDecision() { return decision; }
    public String getReasonCodesJson() { return reasonCodesJson; }
    public String getPolicyVersion() { return policyVersion; }
    public String getQueryType() { return queryType; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }

    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setReasonCodesJson(String reasonCodesJson) { this.reasonCodesJson = reasonCodesJson; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
}
