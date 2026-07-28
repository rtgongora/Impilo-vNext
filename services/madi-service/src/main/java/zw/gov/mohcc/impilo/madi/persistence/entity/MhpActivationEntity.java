package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Massive Haemorrhage Protocol activation (madi V200, W8a) — the protocol-level event, distinct
 * from {@code BloodOrderService.emergencyRelease} which releases one existing blood order.
 */
@Entity
@Table(name = "madi_mhp_activation")
public class MhpActivationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "activation_id")
    private UUID activationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "patient_cpid")
    private String patientCpid;

    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "emergency_episode_id")
    private UUID emergencyEpisodeId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "activated_by", nullable = false)
    private String activatedBy;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;

    @Column(name = "purpose_of_use", nullable = false)
    private String purposeOfUse;

    @Column(name = "stood_down_by")
    private String stoodDownBy;

    @Column(name = "stood_down_at")
    private OffsetDateTime stoodDownAt;

    @Column(name = "stand_down_reason", columnDefinition = "TEXT")
    private String standDownReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (activatedAt == null) activatedAt = now;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public UUID getEmergencyEpisodeId() { return emergencyEpisodeId; }
    public void setEmergencyEpisodeId(UUID emergencyEpisodeId) { this.emergencyEpisodeId = emergencyEpisodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public String getStoodDownBy() { return stoodDownBy; }
    public void setStoodDownBy(String stoodDownBy) { this.stoodDownBy = stoodDownBy; }
    public OffsetDateTime getStoodDownAt() { return stoodDownAt; }
    public void setStoodDownAt(OffsetDateTime stoodDownAt) { this.stoodDownAt = stoodDownAt; }
    public String getStandDownReason() { return standDownReason; }
    public void setStandDownReason(String standDownReason) { this.standDownReason = standDownReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
