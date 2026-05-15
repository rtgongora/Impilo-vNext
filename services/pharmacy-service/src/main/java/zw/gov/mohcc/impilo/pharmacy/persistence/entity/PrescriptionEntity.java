package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.pharmacy.domain.PrescriptionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Canonical prescription record owned by pharmacy-service.
 */
@Entity
@Table(name = "rx_prescriptions")
public class PrescriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "patient_cpid", nullable = false, length = 128)
    private String patientCpid;

    @Column(name = "encounter_id", length = 128)
    private String encounterId;

    @Column(name = "medication_name", nullable = false, length = 255)
    private String medicationName;

    @Column(name = "generic_name", length = 255)
    private String genericName;

    @Column(name = "dosage", length = 128)
    private String dosage;

    @Column(name = "route", length = 64)
    private String route;

    @Column(name = "frequency", length = 128)
    private String frequency;

    @Column(name = "duration", length = 128)
    private String duration;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "indication", columnDefinition = "TEXT")
    private String indication;

    @Column(name = "prescribed_by", nullable = false, length = 128)
    private String prescribedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PrescriptionStatus status = PrescriptionStatus.ACTIVE;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancelled_by", length = 128)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "dispensed_by", length = 128)
    private String dispensedBy;

    @Column(name = "dispensed_at")
    private OffsetDateTime dispensedAt;

    @Column(name = "refill_requested_at")
    private OffsetDateTime refillRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(UUID prescriptionId) { this.prescriptionId = prescriptionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }
    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getIndication() { return indication; }
    public void setIndication(String indication) { this.indication = indication; }
    public String getPrescribedBy() { return prescribedBy; }
    public void setPrescribedBy(String prescribedBy) { this.prescribedBy = prescribedBy; }
    public PrescriptionStatus getStatus() { return status; }
    public void setStatus(PrescriptionStatus status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getDispensedBy() { return dispensedBy; }
    public void setDispensedBy(String dispensedBy) { this.dispensedBy = dispensedBy; }
    public OffsetDateTime getDispensedAt() { return dispensedAt; }
    public void setDispensedAt(OffsetDateTime dispensedAt) { this.dispensedAt = dispensedAt; }
    public OffsetDateTime getRefillRequestedAt() { return refillRequestedAt; }
    public void setRefillRequestedAt(OffsetDateTime refillRequestedAt) { this.refillRequestedAt = refillRequestedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
