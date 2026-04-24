package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "biometric_policy_rule", schema = "tshepo")
public class BiometricPolicyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(name = "context_type", nullable = false, length = 64)
    private String contextType;

    @Column(name = "modality", length = 30)
    private String modality;

    @Column(name = "biometric_intent", nullable = false, length = 32)
    private String biometricIntent;

    @Column(name = "policy_outcome", nullable = false, length = 20)
    private String policyOutcome;

    @Column(name = "enrollment_allowed", nullable = false)
    private boolean enrollmentAllowed;

    @Column(name = "verification_allowed", nullable = false)
    private boolean verificationAllowed;

    @Column(name = "identification_allowed", nullable = false)
    private boolean identificationAllowed;

    @Column(name = "dedup_support_allowed", nullable = false)
    private boolean dedupSupportAllowed;

    @Column(name = "fallback_allowed", nullable = false)
    private boolean fallbackAllowed;

    @Column(name = "notes")
    private String notes;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "actor_type", length = 50)
    private String actorType;

    @Column(name = "min_assurance_level")
    private Short minAssuranceLevel;

    @Column(name = "max_assurance_level")
    private Short maxAssuranceLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public String getContextType() {
        return contextType;
    }

    public String getModality() {
        return modality;
    }

    public String getBiometricIntent() {
        return biometricIntent;
    }

    public String getPolicyOutcome() {
        return policyOutcome;
    }

    public boolean isEnrollmentAllowed() {
        return enrollmentAllowed;
    }

    public boolean isVerificationAllowed() {
        return verificationAllowed;
    }

    public boolean isIdentificationAllowed() {
        return identificationAllowed;
    }

    public boolean isDedupSupportAllowed() {
        return dedupSupportAllowed;
    }

    public boolean isFallbackAllowed() {
        return fallbackAllowed;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public int getPriority() {
        return priority;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public Short getMinAssuranceLevel() {
        return minAssuranceLevel;
    }

    public void setMinAssuranceLevel(Short minAssuranceLevel) {
        this.minAssuranceLevel = minAssuranceLevel;
    }

    public Short getMaxAssuranceLevel() {
        return maxAssuranceLevel;
    }

    public void setMaxAssuranceLevel(Short maxAssuranceLevel) {
        this.maxAssuranceLevel = maxAssuranceLevel;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public void setBiometricIntent(String biometricIntent) {
        this.biometricIntent = biometricIntent;
    }

    public void setPolicyOutcome(String policyOutcome) {
        this.policyOutcome = policyOutcome;
    }

    public void setEnrollmentAllowed(boolean enrollmentAllowed) {
        this.enrollmentAllowed = enrollmentAllowed;
    }

    public void setVerificationAllowed(boolean verificationAllowed) {
        this.verificationAllowed = verificationAllowed;
    }

    public void setIdentificationAllowed(boolean identificationAllowed) {
        this.identificationAllowed = identificationAllowed;
    }

    public void setDedupSupportAllowed(boolean dedupSupportAllowed) {
        this.dedupSupportAllowed = dedupSupportAllowed;
    }

    public void setFallbackAllowed(boolean fallbackAllowed) {
        this.fallbackAllowed = fallbackAllowed;
    }

    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
