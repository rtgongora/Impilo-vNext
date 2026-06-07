package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "donation_drives", schema = "madi")
public class DonationDriveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drive_id", nullable = false, unique = true)
    private UUID driveId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "title", nullable = false)
    private String title;

@Column(name = "description", columnDefinition = "TEXT")
    private String description;

@Column(name = "status")
    private String status;

@Column(name = "drive_type")
    private String driveType;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "location_ref")
    private String locationRef;

@Column(name = "ndila_site_ref")
    private String ndilaSiteRef;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "start_at")
    private OffsetDateTime startAt;

@Column(name = "end_at")
    private OffsetDateTime endAt;

@Column(name = "capacity")
    private Integer capacity;

@Column(name = "published_by")
    private String publishedBy;

@Column(name = "created_by")
    private String createdBy;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (driveId == null) {
            driveId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDriveType() { return driveType; }
    public void setDriveType(String driveType) { this.driveType = driveType; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getLocationRef() { return locationRef; }
    public void setLocationRef(String locationRef) { this.locationRef = locationRef; }

    public String getNdilaSiteRef() { return ndilaSiteRef; }
    public void setNdilaSiteRef(String ndilaSiteRef) { this.ndilaSiteRef = ndilaSiteRef; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }

    public OffsetDateTime getEndAt() { return endAt; }
    public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}