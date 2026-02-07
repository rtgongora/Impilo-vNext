package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.MovementType;

/**
 * Immutable ledger entry recording a stock quantity change.
 * Stock balance is derived by summing qtyDelta for a given facility/store/item.
 */
@Entity
@Table(name = "rx_stock_movements")
public class StockMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "movement_id", nullable = false)
    private UUID movementId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private String storeId;

    @Column(name = "bin_id")
    private String binId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "item_display")
    private String itemDisplay;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "qty_delta", nullable = false)
    private int qtyDelta;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getMovementId() { return movementId; }
    public void setMovementId(UUID movementId) { this.movementId = movementId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getBinId() { return binId; }
    public void setBinId(String binId) { this.binId = binId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemDisplay() { return itemDisplay; }
    public void setItemDisplay(String itemDisplay) { this.itemDisplay = itemDisplay; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public int getQtyDelta() { return qtyDelta; }
    public void setQtyDelta(int qtyDelta) { this.qtyDelta = qtyDelta; }

    public MovementType getMovementType() { return movementType; }
    public void setMovementType(MovementType movementType) { this.movementType = movementType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
