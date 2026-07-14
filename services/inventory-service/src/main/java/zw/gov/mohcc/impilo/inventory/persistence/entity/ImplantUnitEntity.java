package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A physically-tracked implantable device unit (finer than a lot). Each unit
 * carries a UDI and/or serial number and is uniquely identified per tenant.
 */
@Entity
@Table(name = "inv_implant_registry")
public class ImplantUnitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "implant_unit_id", nullable = false)
    private UUID implantUnitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "udi", length = 128)
    private String udi;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "lot_number", length = 128)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(name = "device_type", length = 128)
    private String deviceType;

    @Column(name = "brand", length = 128)
    private String brand;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "batch_lot_id")
    private UUID batchLotId;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "REGISTERED";

    @Column(name = "created_by", length = 128)
    private String createdBy;

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

    public UUID getImplantUnitId() { return implantUnitId; }
    public void setImplantUnitId(UUID implantUnitId) { this.implantUnitId = implantUnitId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getUdi() { return udi; }
    public void setUdi(String udi) { this.udi = udi; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public UUID getBatchLotId() { return batchLotId; }
    public void setBatchLotId(UUID batchLotId) { this.batchLotId = batchLotId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
