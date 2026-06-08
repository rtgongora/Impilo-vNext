package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_stock_movements", schema = "madi")
public class BloodStockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_id", nullable = false, unique = true)
    private UUID movementId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "unit_id")
    private UUID unitId;

@Column(name = "inventory_item_code", nullable = false)
    private String inventoryItemCode;

@Column(name = "movement_type", nullable = false)
    private String movementType;

@Column(name = "quantity")
    private Integer quantity;

@Column(name = "reference_type")
    private String referenceType;

@Column(name = "reference_id")
    private String referenceId;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "moved_by")
    private String movedBy;

@Column(name = "moved_at")
    private OffsetDateTime movedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (movementId == null) {
            movementId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getMovementId() { return movementId; }
    public void setMovementId(UUID movementId) { this.movementId = movementId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public String getInventoryItemCode() { return inventoryItemCode; }
    public void setInventoryItemCode(String inventoryItemCode) { this.inventoryItemCode = inventoryItemCode; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getMovedBy() { return movedBy; }
    public void setMovedBy(String movedBy) { this.movedBy = movedBy; }

    public OffsetDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(OffsetDateTime movedAt) { this.movedAt = movedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}