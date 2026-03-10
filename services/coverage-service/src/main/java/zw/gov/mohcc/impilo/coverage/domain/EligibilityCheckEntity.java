package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "decision_evidence_json", columnDefinition = "TEXT")
    private String decisionEvidenceJson;

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
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
