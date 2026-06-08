package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "donation_drive_attendances", schema = "madi")
public class DonationDriveAttendanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_id", nullable = false, unique = true)
    private UUID attendanceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "drive_id")
    private UUID driveId;

@Column(name = "slot_id")
    private UUID slotId;

@Column(name = "donor_id")
    private UUID donorId;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "registered_at")
    private OffsetDateTime registeredAt;

@Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (attendanceId == null) {
            attendanceId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getAttendanceId() { return attendanceId; }
    public void setAttendanceId(UUID attendanceId) { this.attendanceId = attendanceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }

    public UUID getDonorId() { return donorId; }
    public void setDonorId(UUID donorId) { this.donorId = donorId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }

    public OffsetDateTime getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(OffsetDateTime checkedInAt) { this.checkedInAt = checkedInAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}