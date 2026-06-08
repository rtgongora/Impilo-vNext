package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_receipts", schema = "madi")
public class BloodReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private UUID receiptId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "dispatch_id")
    private UUID dispatchId;

@Column(name = "status")
    private String status;

@Column(name = "received_by")
    private String receivedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDispatchId() { return dispatchId; }
    public void setDispatchId(UUID dispatchId) { this.dispatchId = dispatchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}