package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bed", schema = "inpatient")
public class BedEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    @Column(name = "bed_number", nullable = false)
    private String bedNumber;

    @Column(name = "bed_type", nullable = false)
    private String bedType = "STANDARD";

    @Column(name = "status", nullable = false)
    private String status = "AVAILABLE";

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_diagnosis")
    private String patientDiagnosis;

    @Column(name = "attending_physician")
    private String attendingPhysician;

    @Column(name = "acuity_level")
    private String acuityLevel;

    @Column(name = "patient_age")
    private Integer patientAge;

    @Column(name = "patient_gender")
    private String patientGender;

    @Column(name = "occupied_at")
    private OffsetDateTime occupiedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
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
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWardId() { return wardId; }
    public void setWardId(UUID wardId) { this.wardId = wardId; }
    public String getBedNumber() { return bedNumber; }
    public void setBedNumber(String bedNumber) { this.bedNumber = bedNumber; }
    public String getBedType() { return bedType; }
    public void setBedType(String bedType) { this.bedType = bedType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPatientDiagnosis() { return patientDiagnosis; }
    public void setPatientDiagnosis(String patientDiagnosis) { this.patientDiagnosis = patientDiagnosis; }
    public String getAttendingPhysician() { return attendingPhysician; }
    public void setAttendingPhysician(String attendingPhysician) { this.attendingPhysician = attendingPhysician; }
    public String getAcuityLevel() { return acuityLevel; }
    public void setAcuityLevel(String acuityLevel) { this.acuityLevel = acuityLevel; }
    public String getPatientGender() { return patientGender; }
    public void setPatientGender(String patientGender) { this.patientGender = patientGender; }
    public Integer getPatientAge() { return patientAge; }
    public void setPatientAge(Integer patientAge) { this.patientAge = patientAge; }
    public OffsetDateTime getOccupiedAt() { return occupiedAt; }
    public void setOccupiedAt(OffsetDateTime occupiedAt) { this.occupiedAt = occupiedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
