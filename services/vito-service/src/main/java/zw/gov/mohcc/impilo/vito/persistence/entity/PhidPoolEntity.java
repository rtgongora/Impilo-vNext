package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "phid_pool", schema = "vito")
public class PhidPoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private String facilityId;

    @Column(name = "phid", nullable = false, unique = true)
    private String phid;

    @Column(name = "health_id", nullable = false, unique = true)
    private UUID healthId;

    @Column(name = "status", nullable = false)
    private String status = "AVAILABLE";

    @Column(name = "reserved_at", nullable = false)
    private OffsetDateTime reservedAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "mrs_patient_id", length = 36)
    private String mrsPatientId;

    @PrePersist
    protected void onCreate() {
        if (reservedAt == null) {
            reservedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public String getPhid() { return phid; }
    public void setPhid(String phid) { this.phid = phid; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(OffsetDateTime reservedAt) { this.reservedAt = reservedAt; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public String getMrsPatientId() { return mrsPatientId; }
    public void setMrsPatientId(String mrsPatientId) { this.mrsPatientId = mrsPatientId; }
}
