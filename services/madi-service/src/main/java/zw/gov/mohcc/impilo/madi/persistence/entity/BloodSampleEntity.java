package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_samples", schema = "madi")
public class BloodSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false, unique = true)
    private UUID sampleId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "order_id")
    private UUID orderId;

@Column(name = "sample_number", nullable = false)
    private String sampleNumber;

@Column(name = "status")
    private String status;

@Column(name = "collected_by")
    private String collectedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (sampleId == null) {
            sampleId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getSampleId() { return sampleId; }
    public void setSampleId(UUID sampleId) { this.sampleId = sampleId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getSampleNumber() { return sampleNumber; }
    public void setSampleNumber(String sampleNumber) { this.sampleNumber = sampleNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCollectedBy() { return collectedBy; }
    public void setCollectedBy(String collectedBy) { this.collectedBy = collectedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(OffsetDateTime collectedAt) { this.collectedAt = collectedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}