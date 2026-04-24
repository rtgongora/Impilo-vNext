package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_verification_event", schema = "vito")
public class BiometricVerificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "health_id")
    private UUID healthId;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "liveness_outcome", length = 64)
    private String livenessOutcome;

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

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getHealthId() {
        return healthId;
    }

    public void setHealthId(UUID healthId) {
        this.healthId = healthId;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getLivenessOutcome() {
        return livenessOutcome;
    }

    public void setLivenessOutcome(String livenessOutcome) {
        this.livenessOutcome = livenessOutcome;
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
