package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_collections", schema = "madi")
public class BloodCollectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_id", nullable = false, unique = true)
    private UUID collectionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "donor_id")
    private UUID donorId;

@Column(name = "drive_id")
    private UUID driveId;

@Column(name = "bag_number", nullable = false)
    private String bagNumber;

@Column(name = "volume_ml")
    private Integer volumeMl;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "collected_by")
    private String collectedBy;

@Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (collectionId == null) {
            collectionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getCollectionId() { return collectionId; }
    public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDonorId() { return donorId; }
    public void setDonorId(UUID donorId) { this.donorId = donorId; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public String getBagNumber() { return bagNumber; }
    public void setBagNumber(String bagNumber) { this.bagNumber = bagNumber; }

    public Integer getVolumeMl() { return volumeMl; }
    public void setVolumeMl(Integer volumeMl) { this.volumeMl = volumeMl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getCollectedBy() { return collectedBy; }
    public void setCollectedBy(String collectedBy) { this.collectedBy = collectedBy; }

    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(OffsetDateTime collectedAt) { this.collectedAt = collectedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}