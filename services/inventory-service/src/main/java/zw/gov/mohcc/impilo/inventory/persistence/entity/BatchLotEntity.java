package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A first-class batch/lot of a commodity, with expiry and governance status.
 *
 * <p>Tenant-scoped and unique per (tenant, item, batch number). Status drives
 * usability — QUARANTINED, RECALLED and EXPIRED batches are blocked from
 * dispensing/administration/use.</p>
 */
@Entity
@Table(name = "inv_batch_lots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "item_id", "batch_number"}))
public class BatchLotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "batch_lot_id", nullable = false)
    private UUID batchLotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "supplier")
    private String supplier;

    @Column(name = "serial_number")
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchLotStatus status = BatchLotStatus.ACTIVE;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getBatchLotId() { return batchLotId; }
    public void setBatchLotId(UUID batchLotId) { this.batchLotId = batchLotId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public BatchLotStatus getStatus() { return status; }
    public void setStatus(BatchLotStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
