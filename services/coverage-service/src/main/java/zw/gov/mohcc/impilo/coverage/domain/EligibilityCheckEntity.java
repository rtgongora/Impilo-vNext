package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_eligibility_checks")
public class EligibilityCheckEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "coverage_id", nullable = false)
    private UUID coverageId;

    @Column(name = "patient_ref", nullable = false, length = 255)
    private String patientRef;

    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Column(name = "result_code", nullable = false, length = 32)
    private String resultCode;

    @Column(name = "result_message", columnDefinition = "TEXT")
    private String resultMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_evidence_json", columnDefinition = "jsonb")
    private String decisionEvidenceJson;

    // Ruvimbo eligibility v2 explainability (spec §12.2).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb")
    private String reasonCodes;

    @Column(name = "ruleset_version", length = 32)
    private String rulesetVersion;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "network_status", length = 24)
    private String networkStatus;

    @Column(name = "plan_version_id")
    private UUID planVersionId;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EligibilityCheckEntity() {}

    public EligibilityCheckEntity(UUID tenantId, String podId, UUID coverageId,
                                   String patientRef, String serviceCode,
                                   String resultCode, String resultMessage,
                                   String decisionEvidenceJson) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.coverageId = coverageId;
        this.patientRef = patientRef;
        this.serviceCode = serviceCode;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.decisionEvidenceJson = decisionEvidenceJson;
        OffsetDateTime now = OffsetDateTime.now();
        this.checkedAt = now;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public String getPatientRef() { return patientRef; }
    public String getServiceCode() { return serviceCode; }
    public String getResultCode() { return resultCode; }
    public String getResultMessage() { return resultMessage; }
    public String getDecisionEvidenceJson() { return decisionEvidenceJson; }
    public String getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String v) { this.reasonCodes = v; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String v) { this.rulesetVersion = v; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(OffsetDateTime v) { this.validUntil = v; }
    public String getNetworkStatus() { return networkStatus; }
    public void setNetworkStatus(String v) { this.networkStatus = v; }
    public UUID getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(UUID v) { this.planVersionId = v; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
