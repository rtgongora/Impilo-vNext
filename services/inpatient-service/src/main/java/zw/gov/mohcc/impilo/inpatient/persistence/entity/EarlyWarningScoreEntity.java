package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "early_warning_score", schema = "inpatient")
public class EarlyWarningScoreEntity {

    @Id
    @Column(name = "score_id")
    private UUID scoreId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "score_type", nullable = false)
    private String scoreType = "NEWS2";

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "components_json")
    private String componentsJson;

    @Column(name = "escalation_required", nullable = false)
    private boolean escalationRequired;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    /** Which paediatric age band's thresholds applied; null for the adult score. */
    @Column(name = "age_band")
    private String ageBand;

    @Column(name = "computed_server_side", nullable = false)
    private boolean computedServerSide;

    @Column(name = "calculator_version")
    private String calculatorVersion;

    /** True when a scored parameter was never observed; such a score is not reassurance. */
    @Column(name = "incomplete", nullable = false)
    private boolean incomplete;

    @Column(name = "missing_parameters")
    private String missingParameters;

    /** Retained when a client-supplied total disagreed with the server calculation. */
    @Column(name = "client_reported_score")
    private Integer clientReportedScore;

    @PrePersist
    void onCreate() {
        if (scoreId == null) scoreId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getScoreId() { return scoreId; }
    public void setScoreId(UUID scoreId) { this.scoreId = scoreId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getComponentsJson() { return componentsJson; }
    public void setComponentsJson(String componentsJson) { this.componentsJson = componentsJson; }
    public boolean isEscalationRequired() { return escalationRequired; }
    public void setEscalationRequired(boolean escalationRequired) { this.escalationRequired = escalationRequired; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
    public String getAgeBand() { return ageBand; }
    public void setAgeBand(String ageBand) { this.ageBand = ageBand; }
    public boolean isComputedServerSide() { return computedServerSide; }
    public void setComputedServerSide(boolean computedServerSide) { this.computedServerSide = computedServerSide; }
    public String getCalculatorVersion() { return calculatorVersion; }
    public void setCalculatorVersion(String calculatorVersion) { this.calculatorVersion = calculatorVersion; }
    public boolean isIncomplete() { return incomplete; }
    public void setIncomplete(boolean incomplete) { this.incomplete = incomplete; }
    public String getMissingParameters() { return missingParameters; }
    public void setMissingParameters(String missingParameters) { this.missingParameters = missingParameters; }
    public Integer getClientReportedScore() { return clientReportedScore; }
    public void setClientReportedScore(Integer clientReportedScore) { this.clientReportedScore = clientReportedScore; }
}
