package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_profile", schema = "vito")
public class BiometricProfileEntity {

    @Id
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType = "CLIENT";

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "biometric_status", nullable = false, length = 32)
    private String biometricStatus = "NONE";

    @Column(name = "assurance_level")
    private Integer assuranceLevel;

    @Column(name = "enrolled_by")
    private String enrolledBy;

    @Column(name = "source_context")
    private String sourceContext;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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

    public String getBiometricStatus() {
        return biometricStatus;
    }

    public void setBiometricStatus(String biometricStatus) {
        this.biometricStatus = biometricStatus;
    }

    public Integer getAssuranceLevel() {
        return assuranceLevel;
    }

    public void setAssuranceLevel(Integer assuranceLevel) {
        this.assuranceLevel = assuranceLevel;
    }

    public String getEnrolledBy() {
        return enrolledBy;
    }

    public void setEnrolledBy(String enrolledBy) {
        this.enrolledBy = enrolledBy;
    }

    public String getSourceContext() {
        return sourceContext;
    }

    public void setSourceContext(String sourceContext) {
        this.sourceContext = sourceContext;
    }

    public boolean isActiveFlag() {
        return activeFlag;
    }

    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
