package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_identification_event", schema = "vito")
public class BiometricIdentificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "top_match_health_id")
    private UUID topMatchHealthId;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "decision_summary", length = 512)
    private String decisionSummary;

    @Column(name = "policy_rule_id")
    private Long policyRuleId;

    @Column(name = "policy_outcome", length = 32)
    private String policyOutcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    public UUID getTopMatchHealthId() {
        return topMatchHealthId;
    }

    public void setTopMatchHealthId(UUID topMatchHealthId) {
        this.topMatchHealthId = topMatchHealthId;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(String decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public Long getPolicyRuleId() {
        return policyRuleId;
    }

    public void setPolicyRuleId(Long policyRuleId) {
        this.policyRuleId = policyRuleId;
    }

    public String getPolicyOutcome() {
        return policyOutcome;
    }

    public void setPolicyOutcome(String policyOutcome) {
        this.policyOutcome = policyOutcome;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
