package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "donor_eligibility_screenings", schema = "madi")
public class DonorEligibilityScreeningEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screening_id", nullable = false, unique = true)
    private UUID screeningId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "donor_id")
    private UUID donorId;

@Column(name = "drive_id")
    private UUID driveId;

@Column(name = "result")
    private String result;

@Column(name = "status")
    private String status;

@Column(name = "screening_notes", columnDefinition = "TEXT")
    private String screeningNotes;

@Column(name = "screened_by")
    private String screenedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "screened_at")
    private OffsetDateTime screenedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (screeningId == null) {
            screeningId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getScreeningId() { return screeningId; }
    public void setScreeningId(UUID screeningId) { this.screeningId = screeningId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDonorId() { return donorId; }
    public void setDonorId(UUID donorId) { this.donorId = donorId; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScreeningNotes() { return screeningNotes; }
    public void setScreeningNotes(String screeningNotes) { this.screeningNotes = screeningNotes; }

    public String getScreenedBy() { return screenedBy; }
    public void setScreenedBy(String screenedBy) { this.screenedBy = screenedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getScreenedAt() { return screenedAt; }
    public void setScreenedAt(OffsetDateTime screenedAt) { this.screenedAt = screenedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}