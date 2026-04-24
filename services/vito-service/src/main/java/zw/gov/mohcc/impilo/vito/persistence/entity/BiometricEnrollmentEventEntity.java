package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_enrollment_event", schema = "vito")
public class BiometricEnrollmentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "workflow_type", nullable = false, length = 64)
    private String workflowType;

    @Column(name = "enrolled_by")
    private String enrolledBy;

    @Column(name = "enrollment_context", length = 128)
    private String enrollmentContext;

    @Column(name = "device_ref")
    private String deviceRef;

    @Column(name = "quality_outcome", length = 64)
    private String qualityOutcome;

    @Column(name = "consent_ref")
    private String consentRef;

    @Column(name = "authorization_ref")
    private String authorizationRef;

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

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
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

    public String getEnrolledBy() {
        return enrolledBy;
    }

    public void setEnrolledBy(String enrolledBy) {
        this.enrolledBy = enrolledBy;
    }

    public String getEnrollmentContext() {
        return enrollmentContext;
    }

    public void setEnrollmentContext(String enrollmentContext) {
        this.enrollmentContext = enrollmentContext;
    }

    public String getDeviceRef() {
        return deviceRef;
    }

    public void setDeviceRef(String deviceRef) {
        this.deviceRef = deviceRef;
    }

    public String getQualityOutcome() {
        return qualityOutcome;
    }

    public void setQualityOutcome(String qualityOutcome) {
        this.qualityOutcome = qualityOutcome;
    }

    public String getConsentRef() {
        return consentRef;
    }

    public void setConsentRef(String consentRef) {
        this.consentRef = consentRef;
    }

    public String getAuthorizationRef() {
        return authorizationRef;
    }

    public void setAuthorizationRef(String authorizationRef) {
        this.authorizationRef = authorizationRef;
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
