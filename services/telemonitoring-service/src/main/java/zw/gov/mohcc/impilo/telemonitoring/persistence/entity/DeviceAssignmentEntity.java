package zw.gov.mohcc.impilo.telemonitoring.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;
import zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DeviceAssignment row (OF-B24, Vol II §15.6) — the clinical leg of the §6.2 three-way
 * device split: which patient, which plan, which clinician accountable. {@code deviceRef}
 * points at iot-ingestion's device registry (digital identity truth); {@code assetRef}
 * optionally points at asset-registry equipment (physical/calibration truth). Neither is
 * duplicated here — this row only BINDS them to the patient and plan.
 *
 * <p><b>One-device-one-patient race guard:</b> {@code deviceActiveGuard} mirrors
 * {@code deviceRef} while the assignment is non-terminal and is NULLed on RETURNED/LOST.
 * The UNIQUE constraint on it makes a second live assignment of the same device a DB
 * integrity violation (NULLs never collide, so closed history accumulates freely).
 * The V003 migration adds a Postgres partial-unique backstop on status itself.</p>
 */
@Entity
@Table(name = "tm_device_assignments", schema = "telemonitoring",
        uniqueConstraints = @UniqueConstraint(name = "uq_tm_device_active_guard",
                columnNames = {"device_active_guard"}))
public class DeviceAssignmentEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "patient_cpid", nullable = false, length = 64)
    private String patientCpid;

    @Column(name = "device_ref", nullable = false, length = 64)
    private String deviceRef;

    @Column(name = "asset_ref", length = 64)
    private String assetRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private DeviceAssignmentStatus status = DeviceAssignmentStatus.ASSIGNED;

    @Column(name = "assignment_type", length = 48)
    private String assignmentType;

    @Column(name = "assigned_by", nullable = false, length = 128)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "activated_by", length = 128)
    private String activatedBy;

    @Column(name = "training_confirmed", nullable = false)
    private boolean trainingConfirmed;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "lifecycle_reason", length = 512)
    private String lifecycleReason;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "device_active_guard", length = 64)
    private String deviceActiveGuard;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (assignedAt == null) assignedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getDeviceRef() { return deviceRef; }
    public void setDeviceRef(String deviceRef) { this.deviceRef = deviceRef; }
    public String getAssetRef() { return assetRef; }
    public void setAssetRef(String assetRef) { this.assetRef = assetRef; }
    public DeviceAssignmentStatus getStatus() { return status; }
    public void setStatus(DeviceAssignmentStatus status) { this.status = status; }
    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
    public boolean isTrainingConfirmed() { return trainingConfirmed; }
    public void setTrainingConfirmed(boolean trainingConfirmed) { this.trainingConfirmed = trainingConfirmed; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime returnedAt) { this.returnedAt = returnedAt; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDeviceActiveGuard() { return deviceActiveGuard; }
    public void setDeviceActiveGuard(String deviceActiveGuard) { this.deviceActiveGuard = deviceActiveGuard; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
