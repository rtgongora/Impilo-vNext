package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_stock_reconciliations", schema = "madi")
public class BloodStockReconciliationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciliation_id", nullable = false, unique = true)
    private UUID reconciliationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "status")
    private String status;

@Column(name = "variance_count")
    private Integer varianceCount;

@Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

@Column(name = "reconciled_by")
    private String reconciledBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reconciliationId == null) {
            reconciliationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(UUID reconciliationId) { this.reconciliationId = reconciliationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getVarianceCount() { return varianceCount; }
    public void setVarianceCount(Integer varianceCount) { this.varianceCount = varianceCount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}