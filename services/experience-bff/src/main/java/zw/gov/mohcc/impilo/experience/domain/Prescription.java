package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    private String dosage;

    private String frequency;

    private String duration;

    private Integer quantity;

    private String status;

    @Column(name = "prescribed_by")
    private String prescribedBy;

    @Column(name = "dispensed_by")
    private String dispensedBy;

    @Column(name = "dispensed_at")
    private OffsetDateTime dispensedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Prescription() {}

    public void dispense(String dispensedBy) {
        this.dispensedBy = dispensedBy;
        this.dispensedAt = OffsetDateTime.now();
        this.status = "DISPENSED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getMedicationName() { return medicationName; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getDuration() { return duration; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getPrescribedBy() { return prescribedBy; }
    public String getDispensedBy() { return dispensedBy; }
    public OffsetDateTime getDispensedAt() { return dispensedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
